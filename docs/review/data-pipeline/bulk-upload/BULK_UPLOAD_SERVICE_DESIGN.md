# Review Bulk Upload Service 설계 문서

**작성일**: 2026-04-22  
**대상**: AWS Shop 리뷰 JSONL bulk upload 파이프라인  
**범위**: JSONL 스트림을 line 단위로 읽고, 각 line을 JSON 파싱 및 `ReviewDto` 변환한 뒤 batch 크기 단위로 묶어 JDBC batch로 저장하는 review bulk upload 설계

---

## 1. 목적

이 문서는 리뷰 JSONL 데이터 수십만 건 이상을 메모리에 모두 올리지 않고, 스트리밍 방식으로 처리한 뒤 JDBC batch로 저장하는 bulk upload 흐름을 정의한다.

핵심 목표는 다음과 같다.

1. JSONL 입력을 streaming 방식으로 읽는다.
2. 각 line을 review 객체로 변환한다.
3. 변환된 객체를 batch size 단위로 묶는다.
4. `review`, `review_image` 테이블에 JDBC batch로 저장한다.
5. 실패한 데이터만 JSONL로 별도 저장해 재업로드할 수 있게 한다.
6. 대용량 입력에서도 메모리 사용량을 제한한다.

이 문서는 설계 문서이며, 구현은 포함하지 않는다.

---

## 2. 문제 정의

review bulk upload는 JSON line 1건을 받아 리뷰 1건과 리뷰 이미지를 저장하는 단건 적재 유스케이스를 반복 호출하는 방식이 아니라, JDBC batch 저장에 적합한 별도 적재 흐름을 제공해야 한다.

- 파일 전체를 `String`으로 읽지 않아야 한다.
- JSONL의 한 줄 한 객체 특성을 이용해 line 단위로 순차 처리해야 한다.
- 특정 line의 JSON 파싱 또는 DTO 변환 실패가 전체 업로드를 중단하지 않아야 한다.
- 파싱된 객체를 별도 wrapper 없이 DTO 그대로 batch에 담아야 한다.
- 객체들을 batch size 단위로 묶어 저장해야 한다.
- JSON 파싱 또는 DTO 변환에 실패한 원본 line을 failed JSONL에 그대로 저장해야 한다.
- repository가 실패 batch로 반환한 DTO는 JSONL로 직렬화해 별도 저장해야 한다.
- 응답에는 저장 성공 row 수를 포함하지 않는다.

---

## 3. 서비스 범위

### 3.1 입력

입력은 JSONL 데이터 스트림이다.

예시 입력 형태:

```text
{"rating": 5.0, "title": "New favorite box", "text": "Although I don't remember signing up for this, I'm happy I did. I love every product included.", "images": [], "asin": "B07KM6T8GV", "parent_asin": "B07KM6T8GV", "user_id": "AFDERNB6BIR3U2DOR3S2KX7KJJXQ", "timestamp": 1616156351887, "helpful_vote": 1, "verified_purchase": true}
{"rating": 5.0, "title": "Big Boy Hearts Bark Box", "text": "There is no other subscription box for dogs like Bark Box.", "images": [{"small_image_url": "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL256_.jpg", "medium_image_url": "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL800_.jpg", "large_image_url": "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL1600_.jpg", "attachment_type": "IMAGE"}], "asin": "B07R7WVRGL", "parent_asin": "B08N5QKX1Y", "user_id": "AEDTXOC3YW6O7P2UPM22VNNRF77A", "timestamp": 1563230263551, "helpful_vote": 3, "verified_purchase": false}
```

입력은 다음 조건을 만족해야 한다.

- 한 줄에 JSON object 1개
- UTF-8 인코딩
- blank line 허용 여부는 정책으로 정한다

입력은 반드시 streaming 방식으로 받아야 한다.

- multipart/form-data는 사용하지 않는다.
- request body stream을 그대로 읽는다.
- 파일이 매우 크므로 전체를 메모리에 적재하지 않는다.

### 3.2 JSONL 데이터 구조

review JSON object의 주요 필드는 다음과 같다.

| JSON field | Review field | DB column | 필수 여부 | 설명 |
| --- | --- | --- | --- | --- |
| `rating` | `rating` | `review.rating` | 필수 | 리뷰 평점 |
| `title` | `title` | `review.title` | 필수 | 리뷰 제목, DB column length 511 |
| `text` | `text` | `review.text` | 필수 | 리뷰 본문, `TEXT` |
| `images` | `images` | `review_image` | 필수 | 리뷰 이미지 배열. 빈 배열 허용 |
| `asin` | `asin` | `review.asin` | 필수 | 리뷰 대상 ASIN |
| `parent_asin` | `parentAsin` | `review.product_id` | 필수 | 상품 parent ASIN. 현재 엔티티에서는 `product_id` 컬럼에 저장한다. |
| `user_id` | `user` | `review.user_id` | 필수 | 리뷰 작성자 user id |
| `timestamp` | `timestamp` | `review.timestamp` | 필수 | 리뷰 작성 시각 epoch millis |
| `helpful_vote` | `helpfulVote` | `review.helpful_vote` | 필수 | 도움 됨 투표 수 |
| `verified_purchase` | `verifiedPurchase` | `review.verified_purchase` | 필수 | 인증 구매 여부 |

`images` 배열의 주요 필드는 다음과 같다.

| JSON field | ReviewImage field | DB column | 필수 여부 |
| --- | --- | --- | --- |
| `small_image_url` | `smallImageUrl` | `review_image.small_image_url` | 선택 |
| `medium_image_url` | `mediumImageUrl` | `review_image.medium_image_url` | 선택 |
| `large_image_url` | `largeImageUrl` | `review_image.large_image_url` | 선택 |
| `attachment_type` | `attachmentType` | `review_image.attachment_type` | 선택 |

실제 `aws-dataset/reviews/Subscription_Boxes.jsonl` 기준 확인 결과:

- 총 16,216건
- 위 top-level field는 모두 존재하며 `null` 값이 없다.
- `images`는 빈 배열일 수 있다.
- 이미지가 있는 경우 image object는 `small_image_url`, `medium_image_url`, `large_image_url`, `attachment_type` 구조다.
- `Review`, `ReviewImage` 엔티티 기준으로 실제 JSON field 중 저장 대상에서 빠진 field는 없다.
- `title` 최대 길이는 176자로 현재 `review.title`의 511자 제한 안에 들어간다.
- `text` 최대 길이는 11,127자로 `TEXT` 컬럼 저장이 필요하다.
- 한 review에 포함된 image 개수는 최대 23개까지 확인된다.
- `rating` 값은 `1.0`, `2.0`, `3.0`, `4.0`, `5.0` 분포로 확인된다.

### 3.3 Entity 및 테이블

대상 엔티티는 `src/main/java/jeong/awsshop/review/domain`에 있다.

- `Review`
- `ReviewImage`

대상 테이블은 다음과 같다.

- `review`
- `review_image`

`review` 테이블 unique key:

| constraint | columns | 목적 |
| --- | --- | --- |
| `uk_review_user_product_time` | `user_id`, `product_id`, `timestamp` | JSONL 원본에 포함된 중복 review의 중복 저장 방지 |

unique key 도입 의도:

- JSONL 데이터 자체에 중복 review가 많다.
- `Review.id`는 Snowflake로 새로 생성되므로 원본 중복 판단 기준이 될 수 없다.
- 동일 사용자가 동일 상품에 동일 timestamp로 남긴 review는 같은 원본 review로 본다.
- 중복 데이터는 실패가 아니라 저장 대상에서 제외할 데이터로 취급한다.

`Review` 저장 컬럼:

- `id`
- `user_id`
- `product_id`
- `asin`
- `rating`
- `title`
- `text`
- `timestamp`
- `verified_purchase`
- `helpful_vote`

`ReviewImage` 저장 컬럼:

- `id`
- `review_id`
- `small_image_url`
- `medium_image_url`
- `large_image_url`
- `attachment_type`

### 3.4 출력

bulk upload는 실패 JSONL 파일명을 반환한다.

- `failedJsonlLocation`: `{filename}.jsonl`

### 3.5 실패 데이터 저장

실패한 데이터는 재업로드를 위해 별도 JSONL 파일로 저장한다.

실패 유형은 크게 두 가지다.

- line 처리 실패: JSON 파싱 실패 또는 `ReviewDto` 변환 실패
- persistence 실패: JDBC batch insert 실패

line 처리 실패는 실패한 원본 line을 그대로 저장하고 다음 line을 계속 처리한다.  
persistence 실패는 실패한 batch DTO를 JSONL로 직렬화해 저장한다.

저장 규칙:

- JSON 파싱 실패 line은 원본 line을 그대로 저장한다.
- DTO 변환 실패 line은 원본 line을 그대로 저장한다.
- persistence 실패 batch는 batch DTO 전체를 저장한다.
- unique key 중복은 중복 데이터 skip으로 보고 저장하지 않는다.
- 성공한 batch는 저장하지 않는다.
- 실패 DTO는 `ObjectMapper.writeValueAsString(dto)`로 직렬화해 저장한다.
- 추후 동일한 batch upload 로직으로 다시 업로드할 수 있어야 한다.
- 실패 저장 디렉터리는 `./aws-dataset/reviews`로 고정한다.
- 파일명은 `?filename=` 쿼리 파라미터로 받는다.
- 저장 시 `.jsonl` 확장자를 자동으로 붙인다.
- append 시 구분자는 줄바꿈으로 한다.
- 동일한 `filename`으로 다시 요청되면 같은 파일에 계속 append한다.
- 실패가 없어도 빈 `.jsonl` 파일을 생성한다.

### 3.6 비범위

이 문서는 다음을 다루지 않는다.

- 파일 업로드 이력 저장
- 분산 처리
- 병렬 worker 처리
- 재시도 큐 오케스트레이션
- chunk 기반 분산 스케줄링
- 상품 존재 여부 검증

---

## 4. 핵심 설계 원칙

### 4.1 Streaming first

입력은 `InputStream` 또는 `Reader` 기반으로 처리한다.

금지 사항:

- 전체 파일을 `String`으로 읽는 방식
- 전체 JSONL을 `List<String>`으로 메모리에 올리는 방식

### 4.2 Parser flow

JSONL은 line 단위로 처리한다.

- `BufferedReader`로 `InputStream`을 UTF-8 line 단위로 읽는다.
- blank line은 건너뛴다.
- `objectMapper.readTree(line)`으로 JSON 파싱을 수행한다.
- `objectMapper.treeToValue(jsonNode, ReviewDto.class)`로 DTO 변환을 수행한다.
- JSON 파싱 실패와 DTO 변환 실패는 각각 catch한다.
- 실패한 line은 원본 문자열 그대로 실패 JSONL에 append한다.
- 실패 line 이후에도 다음 line 처리를 계속한다.
- 별도 line wrapper, line 번호, raw line 저장 모델은 만들지 않는다.

### 4.3 Batch first

bulk upload는 단건 insert를 반복하는 방식이 아니라, JDBC batch 저장을 우선으로 설계한다.

이유:

- 단건 insert 반복은 시간이 너무 오래 걸린다.
- JDBC batch는 DB round-trip 수를 줄인다.
- 대용량 업로드에 더 적합하다.

### 4.4 Summary over hard fail

bulk upload의 기본 정책은 "가능한 만큼 끝까지 batch로 처리하고 결과를 요약"하는 것이다.

즉, 일부 line이 실패해도 전체 요청은 중단하지 않고, 실패한 line만 별도 JSONL로 저장한다.

---

## 5. 제안 패키지 구조

```text
jeong.awsshop.review
├── controller
│   └── ReviewBulkUploadController
├── service
│   └── ReviewBulkUploadService
├── dto
│   ├── ReviewBulkUploadResponse
│   └── ReviewDto
└── repository
    └── ReviewBulkInsertRepository
```

review bulk upload는 초기 데이터 적재용 유스케이스다.  
초기 적재 이후 반복적으로 확장될 가능성이 낮으므로 parser, batch assembler, failure writer, batch writer를 별도 클래스로 잘게 분리하지 않는다.

설계상 책임은 다음과 같다.

- `ReviewBulkUploadController`: HTTP 요청 수신, `batch-size`와 `filename` 파라미터 수신, 스트림 전달
- `ReviewBulkUploadService`: line 단위 JSON 파싱, DTO 변환, batch 조립, repository 호출, 실패 JSONL 저장
- `ReviewBulkInsertRepository`: `review`, `review_image` 테이블 batch 저장의 persistence 경계

`ReviewBulkUploadService` 내부 private method 후보:

- `parseLine(BufferedWriter writer, String line)`
- `writeFailedRow(BufferedWriter writer, String line)`
- `writeFailedRows(BufferedWriter writer, List<ReviewDto> failedRows)`

위 메서드는 구현 편의를 위한 내부 구조이며 별도 public API로 고정하지 않는다.

---

## 6. API 설계

### 6.1 업로드 API

예시 엔드포인트:

```http
POST /api/reviews/bulk-upload/jsonl?batch-size=100&filename=failed-reviews
```

요청 형식은 `application/octet-stream` 또는 `text/plain` body stream이다.

이유:

- 파일이 매우 커서 multipart 업로드를 전제로 할 수 없다.
- controller가 request body stream을 그대로 읽어야 한다.

### 6.2 batch-size 쿼리 파라미터

controller는 `?batch-size=` 파라미터를 받는다.

정책:

- `batch-size`는 양의 정수여야 한다.
- 값이 없으면 기본 batch size `100`을 사용한다.
- 최소값은 `1`이다.
- 최대값은 `1000`이다.
- 범위를 벗어나면 요청을 거절한다.

### 6.3 filename 쿼리 파라미터

controller는 `?filename=` 파라미터를 받는다.

정책:

- 실패 JSONL 파일명으로 사용한다.
- 비어 있으면 요청을 거절한다.
- 확장자는 입력받지 않는다.
- 저장 시 자동으로 `.jsonl`을 붙인다.
- 응답은 `{filename}.jsonl` 형태로 반환한다.

### 6.4 응답 API

응답은 실패 JSONL 파일 위치만 포함한다. 저장 성공 row 수는 응답에 반영하지 않는다.

권장 응답 필드:

- `failedJsonlLocation`

정의:

- `failedJsonlLocation`: `{filename}.jsonl`

---

## 7. Controller 책임

`ReviewBulkUploadController`는 HTTP 계층만 담당한다.

담당 책임:

- 업로드 요청 수신
- `batch-size` 파라미터 수신
- `filename` 파라미터 수신
- body stream 획득
- `ReviewBulkUploadService` 호출
- bulk upload 응답 반환

담당하지 않을 책임:

- JSON 파싱
- line 분기
- batch 조립
- DB 저장 세부 처리
- 실패 데이터 JSONL 저장 세부 처리
- 트랜잭션 관리

### 7.1 Controller 처리 원칙

- request body를 한 번에 문자열로 읽지 않는다.
- 파일이 비어 있으면 service 호출 전에 검증한다.
- content type 검증은 선택 사항이지만, 최소한 입력이 stream 가능한 형태인지 확인해야 한다.
- `batch-size`는 controller 단계에서 1차 검증한다.
- `filename`은 controller 단계에서 1차 검증한다.

---

## 8. Service 책임

`ReviewBulkUploadService`는 bulk upload의 orchestration 계층이다.

담당 책임:

- `InputStream`을 UTF-8 `BufferedReader`로 line 단위 순회
- 각 line을 JSON object로 파싱
- JSON object를 `ReviewDto`로 변환
- JSON 파싱 실패 또는 DTO 변환 실패 line을 failed JSONL에 append
- batch size 단위로 객체 묶기
- `ReviewBulkInsertRepository`에 batch write 요청 수행
- 실패 JSONL 파일 생성 및 append
- 실패 JSONL 파일명 반환

담당하지 않을 책임:

- JDBC 세부 구현

### 8.1 stream 처리 흐름

stream은 다음 순서로 처리한다.

1. 실패 JSONL 디렉터리를 준비한다.
2. 실패 JSONL writer를 `CREATE`, `APPEND` 옵션으로 연다.
3. request `InputStream`을 UTF-8 `BufferedReader`로 감싼다.
4. `readLine()`으로 한 줄씩 읽는다.
5. blank line이면 건너뛴다.
6. `objectMapper.readTree(line)`으로 JSON 파싱을 수행한다.
7. JSON 파싱에 실패하면 원본 line을 failed JSONL에 append하고 다음 line으로 넘어간다.
8. `objectMapper.treeToValue(jsonNode, ReviewDto.class)`로 DTO 변환을 수행한다.
9. DTO 변환에 실패하면 원본 line을 failed JSONL에 append하고 다음 line으로 넘어간다.
10. 정상 변환된 `ReviewDto`를 현재 buffer에 추가한다.
11. buffer 크기가 batch size 이상이면 repository에 전달한다.
12. repository가 반환한 실패 DTO가 있으면 실패 JSONL에 직렬화해 기록한다.
13. buffer를 비운다.
14. stream 종료 후 남은 buffer도 동일하게 insert한다.

### 8.2 응답 정책

서비스는 성공/실패 row count를 계산하지 않는다.

- repository 실패 batch는 실패 JSONL로 기록한다.
- controller 응답에는 실패 JSONL 파일명만 담는다.

### 8.3 실패 정책

기본 정책은 continue-on-line-failure와 continue-on-batch-failure다.

- JSON 파싱 실패는 해당 line만 failed JSONL에 기록하고 다음 line을 계속 처리한다.
- DTO 변환 실패는 해당 line만 failed JSONL에 기록하고 다음 line을 계속 처리한다.
- batch write 실패 시 해당 batch를 실패로 분리한다.
- 일반 DB 오류는 실패 batch DTO 전체를 JSONL로 저장한다.
- unique key 중복 오류는 중복 데이터 방지 목적의 정상 skip으로 보고 failed JSONL에 저장하지 않는다.
- 실패 JSONL 저장 형식은 실패 유형에 따라 다르다.
- line 처리 실패는 raw line 그대로 저장한다.
- persistence 실패는 `ReviewDto` 직렬화 결과를 저장한다.

---

## 9. Repository 책임

bulk upload는 별도 upload 이력 테이블 없이 시작하는 것을 기본안으로 둔다.

따라서 repository 책임은 JDBC batch 저장을 위한 persistence 경계로 정의한다.

### 9.1 주요 repository

- `ReviewBulkInsertRepository`

### 9.2 repository 사용 방식

bulk upload service는 단건 insert를 반복 호출하지 않는다.

대신 하나의 batch 안에서 `Review`를 먼저 한 번에 저장하고, 그 다음 `ReviewImage`를 한 번에 저장한다.  
`ReviewBulkInsertRepository`는 `Review` 저장 후 `ReviewImage` 저장을 같은 트랜잭션 안에서 순서대로 수행한다.

이 구조의 장점:

- round-trip 수를 줄일 수 있다.
- 대량 저장 성능을 높일 수 있다.
- 실패 batch만 별도로 재시도하기 쉽다.

### 9.3 저장 원칙

- 하나의 batch 안에 `Review`와 `ReviewImage` 저장을 함께 묶는다.
- 저장 순서는 `Review` 먼저, `ReviewImage` 다음이다.
- 일반 저장 실패 시 repository가 해당 batch DTO list를 그대로 반환하고, service는 이를 실패 JSONL로 분리한다.
- unique key 중복 실패는 중복 데이터로 판단하고 repository가 빈 list를 반환해 skip한다.
- `Review.id`와 `ReviewImage.id`는 `SnowflakeIdGenerator`로 생성한다.
- `ReviewImage.review_id`는 같은 batch에서 생성한 `Review.id`를 참조한다.
- JSON의 `parent_asin`은 현재 엔티티 매핑에 따라 `review.product_id` 컬럼에 저장한다.
- 현재 `ReviewImage` 엔티티 builder는 `id`와 `attachmentType`을 받지 않는다. JDBC batch insert에서는 엔티티 builder에 의존하지 않고 SQL parameter로 `review_image.id`와 `review_image.attachment_type`을 직접 세팅한다.
- 만약 JPA entity 생성 방식으로 구현을 바꾸면 `ReviewImage` 생성 시 `id`와 `attachmentType`을 주입할 수 있도록 엔티티 생성자 또는 factory 수정이 필요하다.

---

## 10. JSON object 처리 상세 설계

### 10.1 blank line

blank line은 service에서 건너뛴다.

- 별도 skipped count를 계산하지 않는다.
- `String.isBlank()` 기준으로 비어 있는 line은 JSON 파싱 대상에 포함하지 않는다.

### 10.2 JSON 파싱 실패

잘못된 JSON line이 들어오면 `objectMapper.readTree(line)` 단계에서 예외가 발생할 수 있다.

요구 사항:

- 로그를 남긴다.
- 실패한 원본 line을 failed JSONL에 그대로 append한다.
- 업로드를 중단하지 않고 다음 line 처리를 계속한다.

### 10.3 DTO 변환 실패

JSON 파싱은 성공했지만 `ReviewDto`로 변환할 수 없는 데이터가 들어오면 `objectMapper.treeToValue(jsonNode, ReviewDto.class)` 단계에서 예외가 발생할 수 있다.

예상 가능한 예:

- `rating`이 숫자가 아니라 object인 경우
- `images`가 배열이 아니라 object인 경우
- `timestamp`, `helpful_vote`, `verified_purchase` 등이 DTO 타입으로 변환 불가능한 경우

요구 사항:

- 로그를 남긴다.
- 실패한 원본 line을 failed JSONL에 그대로 append한다.
- 업로드를 중단하지 않고 다음 line 처리를 계속한다.

### 10.4 필수 필드 누락

필수 필드:

- `rating`
- `title`
- `text`
- `images`
- `asin`
- `parent_asin`
- `user_id`
- `timestamp`
- `verified_purchase`
- `helpful_vote`

bulk upload service는 Product bulk upload와 동일하게 별도 필드 검증 클래스를 두지 않는다.  
필수성은 DTO 역직렬화와 DB insert 시점의 제약으로 드러난다.

문자열 field의 빈 문자열 정책:

- `asin`, `parent_asin`, `user_id`는 입력 값을 그대로 DB parameter로 전달한다.
- `title`, `text`도 입력 값을 그대로 DB parameter로 전달한다.
- `images`는 빈 배열을 허용한다.

### 10.5 필드 값 검증

별도 값 검증은 service에서 수행하지 않는다.

- `rating`, `timestamp`, `helpful_vote`, `verified_purchase`는 Jackson이 `ReviewDto` 타입으로 변환한다.
- DB 컬럼 제약이나 타입 문제로 insert가 실패하면 repository가 batch 전체를 실패 list로 반환한다.
- `title` 길이 초과도 service에서 truncate하지 않고 DB insert 결과에 맡긴다.

### 10.6 image 검증

`images`는 `List<ReviewImageDto>`로 역직렬화한다.

정책:

- `images`가 빈 배열이면 정상 처리한다.
- image object의 `small_image_url`, `medium_image_url`, `large_image_url`, `attachment_type`은 선택 field다.
- service는 image row를 필터링하지 않는다.
- `attachment_type`은 `review_image.attachment_type`에 저장한다.
- 알 수 없는 image field는 무시한다.

### 10.7 중복 review

review JSONL에는 중복 데이터가 많을 수 있다.  
중복 방지는 애플리케이션 메모리에서 set을 유지하는 방식이 아니라 DB unique key로 처리한다.

- `Review.id`는 내부에서 새로 생성되므로 중복 판단 기준으로 사용하지 않는다.
- `review` 테이블은 `user_id`, `product_id`, `timestamp` 조합에 unique key를 가진다.
- unique constraint 이름은 `uk_review_user_product_time`이다.
- 의도는 JSONL 원본에 포함된 중복 review의 중복 저장을 방지하는 것이다.
- repository는 unique key 위반을 중복 데이터로 보고 skip한다.
- unique key 위반 batch는 실패 JSONL에 저장하지 않는다.
- 중복 skip은 실패가 아니라 이미 저장된 데이터 또는 같은 업로드 내 중복 데이터에 대한 멱등성 처리로 본다.

주의:

- 현재 JDBC batch 단위 insert에서 unique key 위반이 발생하면 해당 batch가 rollback될 수 있다.
- repository는 unique key 위반을 감지하면 빈 실패 list를 반환한다.
- 따라서 중복이 포함된 batch 내의 비중복 row도 함께 저장되지 않을 수 있다.
- 이 정책은 "중복 때문에 업로드 전체가 중단되지 않게 한다"는 목표를 우선한다.
- 필요하면 향후 batch split 또는 row-level retry로 중복 row만 분리하는 개선을 별도 설계한다.

### 10.8 product 존재 여부 검증

`parent_asin`은 실제 product 존재 여부를 검증하지 않는다.

- product가 없는 review도 저장 대상이다.
- `parent_asin`은 review 원본 데이터의 식별 문자열로 취급한다.
- bulk upload 단계에서 `product` 테이블을 조회하지 않는다.
- `review.product_id`는 FK 검증 대상이 아니라 문자열 저장 대상이다.

### 10.9 알 수 없는 field 처리

원본 JSON에 정의되지 않은 추가 field 처리는 `ObjectMapper` 설정과 DTO 매핑 정책을 따른다.

정책:

- 현재 `ReviewDto`에는 명시적인 `@JsonIgnoreProperties(ignoreUnknown = true)`가 없다.
- DTO 변환 단계에서 unknown field가 예외를 유발하면 해당 원본 line을 failed JSONL에 그대로 저장하고 다음 line을 계속 처리한다.
- ObjectMapper 설정상 unknown field가 무시되는 경우에는 정상 DTO로 변환되어 저장될 수 있다.
- line 처리 실패로 저장되는 경우에는 원본 line을 그대로 저장하므로 unknown field도 보존된다.
- persistence 실패로 저장되는 경우에는 `ReviewDto` 직렬화 결과를 저장하므로 DTO에 없는 unknown field는 보존되지 않는다.

### 10.10 persistence 실패

DB 저장 중 예외가 발생하면 해당 batch를 실패로 처리한다.

예상 가능한 원인:

- `review_image.review_id` 내부 참조 오류
- NOT NULL 제약 위반
- 컬럼 길이 초과
- JDBC batch 실행 실패
- unique key 중복

정책:

- unique key 중복은 중복 데이터 방지 목적이므로 실패 JSONL에 저장하지 않고 skip한다.
- 그 외 SQL 예외 또는 예상하지 못한 예외는 실패 batch DTO 전체를 failed JSONL에 저장할 수 있도록 repository가 batch list를 반환한다.
- MySQL JDBC batch에서는 `BatchUpdateException`이 발생해도 정확한 실패 row를 항상 역산할 수 없으므로, 일반 실패는 batch DTO 전체를 저장하는 정책으로 단순화한다.

---

## 11. 트랜잭션 설계

### 11.1 batch 단위 트랜잭션

각 batch는 독립 트랜잭션으로 처리하는 것을 기본안으로 둔다.

이유:

- 실패 batch 롤백이 간단하다.
- 성공 batch의 저장 결과를 유지할 수 있다.
- bulk upload에서 partial success를 허용하기 쉽다.

권장 흐름:

1. batch 내부의 각 review에 대해 `Review.id`를 생성한다.
2. `review` 테이블 batch insert를 수행한다.
3. 각 review의 image에 대해 `ReviewImage.id`를 생성하고 `review_id`를 연결한다.
4. `review_image` 테이블 batch insert를 수행한다.
5. 동일 batch 트랜잭션 안에서 commit 또는 rollback한다.

주의:

- `Review` 저장과 `ReviewImage` 저장 사이에 중간 실패가 발생하면 동일 batch 전체를 rollback해야 한다.
- `ReviewImage` 저장을 위해서는 batch 내부에서 생성한 `Review.id` 매핑이 필요하다.

### 11.2 전체 트랜잭션 비권장

전체 파일을 하나의 트랜잭션으로 묶지 않는다.

이유:

- 대용량에서 트랜잭션이 너무 길어진다.
- 실패 하나 때문에 전체 롤백되면 bulk upload의 장점이 사라진다.

---

## 12. batch 저장 전략

bulk upload는 단건 insert를 반복하지 않고 batch 저장을 직접 수행하는 방향으로 설계한다.

권장 흐름:

```text
HTTP 요청
  -> ReviewBulkUploadController
  -> ReviewBulkUploadService
  -> BufferedReader line reader
  -> ObjectMapper.readTree(line)
  -> ObjectMapper.treeToValue(jsonNode, ReviewDto.class)
  -> ReviewDto buffer
  -> JDBC batch writer
  -> failed JSONL location response
```

이 방식의 장점:

- 단건 insert 반복보다 빠르다.
- DB round-trip을 줄일 수 있다.
- batch size를 통해 처리량을 제어할 수 있다.

### 12.1 batch size 정책

batch size는 controller query parameter로 받는다.

정책:

- 최소 1 이상이어야 한다.
- 운영 안정성을 위해 상한을 둔다.
- 기본값은 `100`이다.
- 상한은 `1000`이다.

### 12.2 SQL 저장 대상

`review` insert:

```sql
INSERT INTO review (
    id, user_id, product_id, asin, rating, title, text, timestamp, verified_purchase, helpful_vote
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```

parameter mapping:

| index | column | source |
| --- | --- | --- |
| 1 | `id` | `SnowflakeIdGenerator.nextId()` |
| 2 | `user_id` | JSON `user_id` |
| 3 | `product_id` | JSON `parent_asin` |
| 4 | `asin` | JSON `asin` |
| 5 | `rating` | JSON `rating` |
| 6 | `title` | JSON `title` |
| 7 | `text` | JSON `text` |
| 8 | `timestamp` | JSON `timestamp` |
| 9 | `verified_purchase` | JSON `verified_purchase` |
| 10 | `helpful_vote` | JSON `helpful_vote` |

`review_image` insert:

```sql
INSERT INTO review_image (
    id, review_id, small_image_url, medium_image_url, large_image_url, attachment_type
) VALUES (?, ?, ?, ?, ?, ?)
```

parameter mapping:

| index | column | source |
| --- | --- | --- |
| 1 | `id` | `SnowflakeIdGenerator.nextId()` |
| 2 | `review_id` | 같은 line에서 생성한 `review.id` |
| 3 | `small_image_url` | JSON image `small_image_url` |
| 4 | `medium_image_url` | JSON image `medium_image_url` |
| 5 | `large_image_url` | JSON image `large_image_url` |
| 6 | `attachment_type` | JSON image `attachment_type` |

### 12.3 failure JSONL 재저장

실패 데이터는 실패 유형에 따라 별도 JSONL 파일에 저장한다.

저장 목적:

- 동일한 파이프라인으로 재업로드
- 실패 원인 수정 후 재처리
- 실패 데이터만 선별 추적

주의:

- JSON 파싱 실패와 DTO 변환 실패는 원본 line을 그대로 append한다.
- 일반 persistence 실패는 batch DTO 전체를 요청 1건당 1개 파일에 append한다.
- unique key 중복은 중복 데이터 방지 목적의 skip이므로 failed JSONL에 기록하지 않는다.
- MySQL JDBC batch에서 일부 row만 실패하더라도, 일반 실패는 해당 batch 전체를 실패로 보고 DTO 직렬화 결과 전체를 append한다.
- 중복 row만 분리하는 처리는 향후 batch splitting 또는 row-level retry 전략으로 별도 설계한다.

---

## 13. 결과 모델

### 13.1 ReviewBulkUploadResponse

bulk upload 응답은 실패 JSONL 파일명만 포함한다.

권장 필드:

- `failedJsonlLocation`

### 13.2 ReviewDto

JSON line을 파싱하기 위한 DTO다.

권장 필드:

- `Float rating`
- `String title`
- `String text`
- `List<ReviewImageDto> images`
- `String asin`
- `String parentAsin`
- `String userId`
- `Long timestamp`
- `Integer helpfulVote`
- `Boolean verifiedPurchase`

primitive type이 아니라 wrapper type을 사용한다.

`ReviewImageDto` 권장 필드:

- `String smallImageUrl`
- `String mediumImageUrl`
- `String largeImageUrl`
- `String attachmentType`

`@JsonProperty` 매핑:

- `parent_asin` -> `parentAsin`
- `user_id` -> `userId`
- `helpful_vote` -> `helpfulVote`
- `verified_purchase` -> `verifiedPurchase`
- `small_image_url` -> `smallImageUrl`
- `medium_image_url` -> `mediumImageUrl`
- `large_image_url` -> `largeImageUrl`
- `attachment_type` -> `attachmentType`

### 13.3 실패 JSONL 저장 정책

실패 저장 파일은 재업로드 가능한 JSONL 형식이어야 한다.

정책:

- line 단위로 저장한다.
- JSON 파싱 실패는 원본 line을 저장한다.
- DTO 변환 실패는 원본 line을 저장한다.
- batch write 실패는 실패한 batch DTO 전체를 저장한다.
- unique key 중복은 저장하지 않는다.
- 성공 DTO는 저장하지 않는다.
- 구분자는 줄바꿈이다.

---

## 14. 예외 정책

bulk upload는 line-level 실패와 batch-level 실패를 분리해서 처리한다.

### 14.1 line-level 예외

아래 예외는 요청 전체를 중단하지 않는다.

- JSON 파싱 실패
- DTO 변환 실패

처리 방식:

- 실패 원본 line을 failed JSONL에 append한다.
- 로그를 남긴다.
- 다음 line 처리를 계속한다.

### 14.2 batch-level 예외

아래 예외는 해당 batch 단위로 처리한다.

- 일반 SQL 예외
- JDBC batch 실행 실패
- 컬럼 제약 위반

처리 방식:

- unique key 중복이면 중복 데이터 skip으로 보고 failed JSONL에 저장하지 않는다.
- 그 외 persistence 실패이면 repository가 실패 batch DTO list를 반환한다.
- service는 반환된 실패 batch DTO를 failed JSONL에 append한다.

### 14.3 request-level 예외

아래는 요청 전체 실패로 볼 수 있다.

- 요청 body를 읽지 못한 경우
- 파일 타입이 아예 잘못된 경우
- I/O 스트림이 끊긴 경우
- 실패 JSONL 파일을 생성할 수 없는 경우

이 경우에는 controller가 4xx 또는 5xx 응답으로 종료할 수 있다.

---

## 15. 성능 및 메모리 고려

### 15.1 메모리 사용 제한

입력은 `BufferedReader`로 line 단위 순차 처리하므로 전체 파일 크기에 비례한 메모리 증가를 피한다.

메모리에 유지하는 데이터는 원칙적으로 다음으로 제한한다.

- 현재 batch의 parsed DTO
- 현재 처리 중인 line
- 실패 JSONL writer

### 15.2 큰 파일 대응

대용량 파일에서는 다음이 중요하다.

- batch 단위 buffering
- 결과 누적 객체 크기 제한
- 실패 상세를 너무 많이 쌓지 않는 정책

필요하면 실패 목록 개수 상한을 둔다. 실패 원본은 메모리에 계속 보관하지 않고 즉시 실패 JSONL writer로 넘기는 방식을 우선한다.

### 15.3 저장 성능

현재 단계에서는 최적화보다 correctness를 우선한다.

나중에 필요하면 다음을 고려할 수 있다.

- 일정 batch 수마다 flush/clear
- chunk 단위 commit
- 병렬 ingestion
- batch 실패 시 binary split으로 실패 row 범위 축소

하지만 이 문서의 1차 목표에는 포함하지 않는다.

---

## 16. 테스트 관점

설계 기준 테스트는 다음을 검증해야 한다.

1. JSONL 스트림을 line 단위로 순차 처리하는가
2. blank line을 건너뛰는가
3. JSON object가 review DTO로 변환되는가
4. snake_case JSON key가 camelCase 필드로 매핑되는가
5. 실제 샘플 JSONL 5건이 모두 정상 파싱되는가
6. JSON 파싱 실패 line을 failed JSONL에 저장하고 다음 line을 계속 처리하는가
7. DTO 변환 실패 line을 failed JSONL에 저장하고 다음 line을 계속 처리하는가
8. `images`가 빈 배열이면 정상 처리되는가
9. `attachment_type`을 저장하고 unknown field를 무시하는가
10. 객체가 batch size 단위로 묶이는가
11. batch size보다 적은 잔여 객체도 insert 되는가
12. `review` batch insert가 수행되는가
13. `review_image` batch insert가 수행되는가
14. repository insert 실패 시 해당 batch DTO list를 반환하는가
15. 일반 batch write 실패 시 해당 batch DTO 전체가 JSONL로 저장되는가
16. unique key 중복 시 업로드가 중단되지 않고 skip되는가
17. `batch-size` 쿼리 파라미터가 반영되는가

---

## 17. 구현 순서

1. controller API 형태 확정
2. `batch-size` 쿼리 파라미터 계약 정의
3. `filename` 쿼리 파라미터 계약 정의
4. stream 입력 계약 정의
5. line 단위 JSON 파싱 및 DTO 변환 정책 정의
6. review parser DTO 정의
7. batch buffer 정의
8. JDBC batch writer 정의
9. unique key 기반 중복 방지 정책 정의
10. 실패 JSONL 저장 정책 정의
11. 실패 JSONL location 응답 DTO 정의
12. 테스트 기준 문서화

---

## 18. 결론

`ReviewBulkUploadService`는 JSONL 파일을 한 번에 문자열로 읽지 않고, line 단위로 JSON 파싱과 DTO 변환을 수행한 뒤 batch size 단위로 묶어 JDBC batch로 저장하는 업로드 오케스트레이터다.

이 유스케이스의 핵심은 다음이다.

1. 메모리 효율성 확보
2. line 단위 JSON 파싱 및 DTO 변환
3. batch size 기반 묶음 처리
4. `review`, `review_image` JDBC batch 저장
5. JSON 파싱/DTO 변환 실패 line의 원본 JSONL 별도 저장
6. 일반 실패 batch DTO JSONL 별도 저장
7. unique key 기반 중복 review 저장 방지
8. 실패 JSONL 파일명 반환

이 설계를 기준으로 controller, service, repository 경계를 먼저 고정한 뒤 구현하면, 이후 chunk 처리나 병렬화 같은 확장도 자연스럽게 붙일 수 있다.
