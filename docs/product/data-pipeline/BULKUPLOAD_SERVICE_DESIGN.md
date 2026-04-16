# BulkUploadService 설계 문서

**작성일**: 2026-04-16  
**대상**: AWS Shop 상품 데이터 적재 파이프라인  
**범위**: JSONL 한 줄을 받아 파싱하고, 루트 엔티티와 하위 엔티티를 생성한 뒤 FK를 연결하여 DB 저장까지 수행하는 서비스 설계

---

## 1. 목적

이 문서는 `BulkUploadService`가 수행해야 할 책임을 정의한다.

서비스의 역할은 다음과 같다.

1. JSON line 1개를 입력받는다.
2. 문자열을 JSON 구조로 파싱한다.
3. JSON 값을 도메인 엔티티로 변환한다.
4. `Product`를 루트 엔티티로 만들고, 하위 엔티티들의 `product` FK를 연결한다.
5. 하나의 상품 단위로 저장한다.
6. 저장 결과를 조회 가능한 형태로 제공한다.

이 문서는 설계 문서이며, 구현은 포함하지 않는다.

---

## 2. 서비스 범위

### 2.1 입력

입력은 JSONL의 한 줄이다.

예시 필드:

```json
{
  "main_category": "Handmade",
  "title": "Daisy Keychain Wristlet Gray Fabric Key fob Lanyard",
  "average_rating": 4.5,
  "rating_number": 12,
  "features": ["High Quality Fabrics", "..."],
  "description": ["This charming Daisy Fabric Keychain wristlet ..."],
  "price": null,
  "images": [
    {"thumb": "...", "large": "...", "variant": "MAIN", "hi_res": null}
  ],
  "videos": [],
  "store": "Generic",
  "categories": ["Handmade Products", "..."],
  "details": {
    "Package Dimensions": "8 x 4 x 0.85 inches; 0.81 Ounces",
    "Department": "womens"
  },
  "parent_asin": "B07NTK7T5P",
  "bought_together": null
}
```

### 2.2 출력

서비스는 다음을 보장해야 한다.

- `Product` 1건 저장
- `ProductFeature`, `ProductDescription`, `ProductCategory`, `ProductImage`, `ProductVideo`, `ProductBoughtTogether`의 FK 연결
- `parentAsin` 기준 조회 가능

### 2.3 비범위

이 문서는 다음을 다루지 않는다.

- 대용량 파일 전체 스트리밍 처리
- 배치 단위 flush 정책
- 재시도 정책
- 메시지 큐 연동
- 스케줄러 설계

---

## 3. 도메인 기준

현재 상품 도메인은 `Product`를 루트로 두고, 하위 엔티티를 1:N 관계로 연결한다.

### 3.1 루트 엔티티

- `Product`

### 3.2 하위 엔티티

- `ProductFeature`
- `ProductDescription`
- `ProductCategory`
- `ProductImage`
- `ProductVideo`
- `ProductBoughtTogether`

### 3.3 FK 연결 원칙

하위 엔티티는 모두 `product_id`를 통해 `Product`를 참조한다.

즉, 생성 순서는 항상 다음과 같다.

1. `Product` 생성
2. `Product` 참조를 가진 child entity 생성
3. `Product` 컬렉션에 child entity 추가
4. cascade 저장 또는 개별 저장

---

## 4. BulkUploadService 책임

`BulkUploadService`는 insert 유스케이스의 오케스트레이터다.

서비스가 담당할 책임:

- JSON line 파싱 요청 수신
- 필드 정규화
- 도메인 엔티티 생성
- FK 연결
- 저장 트랜잭션 관리
- 저장 후 조회 가능 상태 보장

서비스가 담당하지 않을 책임:

- JSON 스키마 정의
- DB 스키마 생성
- 검색 인덱싱
- 파일 I/O
- 배치 chunk 관리

---

## 5. 제안 패키지 구조

```text
jeong.awsshop.product.application
├── BulkUploadService
├── ProductLineParser
├── ProductEntityFactory
├── ProductRelationFactory
├── ProductReadService
└── ProductWriteService
```

설계상 책임은 다음과 같이 분리한다.

- `BulkUploadService`: 유스케이스 진입점
- `ProductLineParser`: JSON line -> 중간 표현 변환
- `ProductEntityFactory`: `Product` 생성
- `ProductRelationFactory`: child entity 생성 및 FK 연결
- `ProductWriteService`: 저장 책임
- `ProductReadService`: 조회 책임

구현 시점에서는 단일 서비스로 시작할 수 있으나, 역할은 위와 같이 분리하는 편이 테스트와 유지보수에 유리하다.

---

## 6. 처리 흐름

### 6.1 전체 흐름

```text
JSON line 1개 수신
  -> JSON 파싱
  -> 필수 필드 검증
  -> Product 생성
  -> child entity 생성
  -> Product 참조 연결
  -> repository save
  -> 저장 결과 반환
```

### 6.2 세부 단계

#### 1) 입력 수신

- 메서드는 `String jsonLine` 1개를 입력받는다.
- 개행 문자는 호출자 책임으로 제거되어 있다고 가정한다.

#### 2) JSON 파싱

- `ObjectMapper` 계열 도구로 JSON 문자열을 파싱한다.
- 파싱 결과는 map 또는 DTO 중 하나로 변환한다.

#### 3) 필수 필드 검증

필수 필드:

- `parent_asin`
- `title`
- `main_category`

검증 규칙:

- `parent_asin`이 없으면 저장하지 않는다.
- `title`이 없으면 저장하지 않는다.
- `main_category`는 알 수 없는 값이면 `UNKNOWN`으로 매핑한다.

#### 4) 루트 엔티티 생성

`Product`를 먼저 생성한다.

루트 엔티티에 포함되는 값:

- `id`
- `parentAsin`
- `title`
- `mainCategory`
- `averageRating`
- `ratingNumber`
- `price`
- `store`
- `details`

#### 5) child entity 생성

배열 또는 객체 필드에 대해 child entity를 생성한다.

- `features` -> `ProductFeature`
- `description` -> `ProductDescription`
- `categories` -> `ProductCategory`
- `images` -> `ProductImage`
- `videos` -> `ProductVideo`
- `bought_together` -> `ProductBoughtTogether`

#### 6) FK 연결

생성된 child entity는 모두 같은 `Product` 인스턴스를 참조해야 한다.

예:

- `ProductFeature.product = product`
- `ProductDescription.product = product`
- `ProductCategory.product = product`
- `ProductImage.product = product`
- `ProductVideo.product = product`
- `ProductBoughtTogether.product = product`

#### 7) 저장

저장 방식은 다음 중 하나를 선택할 수 있다.

- `Product`에 cascade가 걸려 있으므로 `Product`만 저장
- 또는 루트와 child를 명시적으로 저장

권장안:

- 루트 엔티티를 기준으로 저장하고, child는 cascade로 함께 저장

---

## 7. 필드별 변환 규칙

### 7.1 `main_category`

- 문자열을 `MainCategory` enum으로 변환한다.
- 매핑되지 않는 값은 `UNKNOWN`으로 보낸다.
- 입력값 예시처럼 `"Handmade"`와 같이 enum 이름과 정확히 일치하지 않는 값은 명시적 매핑 테이블로 정규화한다.
- `main_category`가 null이면 저장하지 않거나 `UNKNOWN`으로 대체하는 정책을 명확히 고정해야 한다.

### 7.2 `features`

- 문자열 배열을 그대로 child entity로 변환한다.
- 공백 문자열은 제외한다.
- 배열 자체가 null이면 빈 리스트로 처리한다.
- 요소가 null 또는 빈 문자열이면 해당 요소는 스킵한다.
- 순서 보존이 필요하면 `featureIndex`를 사용한다.

### 7.3 `description`

- 문자열 배열을 child entity로 변환한다.
- 배열 자체가 null이면 빈 리스트로 처리한다.
- 요소가 null 또는 빈 문자열이면 해당 요소는 스킵한다.
- 순서 보존이 필요하면 `descriptionIndex`를 사용한다.

### 7.4 `categories`

- 문자열 배열을 child entity로 변환한다.
- 배열 자체가 null이면 빈 리스트로 처리한다.
- 요소가 null 또는 빈 문자열이면 해당 요소는 스킵한다.
- 계층 구조는 문자열 순서를 유지한다.

### 7.5 `images`

- 객체 배열을 `ProductImage`로 변환한다.
- `thumb`, `large`, `variant`, `hi_res`를 각각 저장한다.
- 배열 자체가 null이면 빈 리스트로 처리한다.
- 객체 내부 필드는 nullable로 보고, null이 들어와도 예외 없이 저장한다.
- 객체 자체가 null이면 해당 요소는 스킵한다.

### 7.6 `videos`

- 객체 배열을 `ProductVideo`로 변환한다.
- `user_id`가 비어 있으면 null 처리 또는 빈 문자열 정책을 명확히 둔다.
- 배열 자체가 null이면 빈 리스트로 처리한다.
- `title`, `url`, `userId`는 현재 엔티티상 nullable로 처리 가능하므로 null 값이 와도 에러가 나지 않게 저장한다.
- 객체 자체가 null이면 해당 요소는 스킵한다.

### 7.7 `details`

- JSON 객체를 문자열로 직렬화해 `Product.details`에 저장한다.
- 직렬화 실패 시 저장을 중단하거나 null 대체 중 하나를 정책으로 둔다.
- `details`가 null이면 직렬화하지 않고 null로 저장한다.

### 7.8 `bought_together`

- 현재 예시처럼 null이 자주 들어올 수 있다.
- null이면 child entity 생성 없이 스킵한다.
- 객체가 존재하더라도 `relatedProductId`가 없으면 저장하지 않는다. 이 필드는 엔티티상 `nullable = false`다.
- `relatedProductTitle`, `relatedProductImageUrl`은 null 허용으로 본다.

---

## 8. 엔티티 null 안전성 점검

다음 필드는 엔티티상 null 허용 여부가 다르므로, 적재 시 예외가 나지 않도록 서비스에서 선제 처리해야 한다.

| 엔티티 | 필드 | null 허용 여부 | 적재 정책 |
|---|---|---:|---|
| `Product` | `parentAsin` | 불허 | null/blank면 저장하지 않음 |
| `Product` | `title` | 사실상 불허 | null/blank면 저장하지 않음 |
| `Product` | `mainCategory` | 불허 | 매핑 불가 또는 null이면 `UNKNOWN`으로 저장 |
| `Product` | `averageRating` | 허용 | null 그대로 저장 |
| `Product` | `ratingNumber` | 허용 | null이면 0으로 저장 |
| `Product` | `price` | 허용 | null 그대로 저장 |
| `Product` | `store` | 허용 | null/blank면 null 저장 |
| `Product` | `details` | 허용 | null 그대로 저장 가능 |
| `ProductFeature` | `product` | 불허 | 부모 없이는 생성하지 않음 |
| `ProductFeature` | `feature` | 불허 | null/blank 요소 스킵 |
| `ProductDescription` | `product` | 불허 | 부모 없이는 생성하지 않음 |
| `ProductDescription` | `description` | 불허 | null/blank 요소 스킵 |
| `ProductCategory` | `product` | 불허 | 부모 없이는 생성하지 않음 |
| `ProductCategory` | `category` | 허용 | null이면 스킵 권장 |
| `ProductImage` | `product` | 불허 | 부모 없이는 생성하지 않음 |
| `ProductImage` | `variant/thumb/large/hiRes` | 허용 | null 허용, 객체 자체 null만 스킵 |
| `ProductVideo` | `product` | 불허 | 부모 없이는 생성하지 않음 |
| `ProductVideo` | `title/url/userId` | 허용 | null 허용, 객체 자체 null만 스킵 |
| `ProductBoughtTogether` | `product` | 불허 | 부모 없이는 생성하지 않음 |
| `ProductBoughtTogether` | `relatedProductId` | 불허 | null이면 생성하지 않음 |
| `ProductBoughtTogether` | `relatedProductTitle/relatedProductImageUrl` | 허용 | null 허용 |

---

## 9. FK 연결 전략

### 9.1 핵심 원칙

child entity는 부모의 생명주기에 종속된다.

따라서 다음 둘 중 하나가 필요하다.

1. `Product`를 먼저 생성하고 child를 `product`에 붙인 뒤 저장
2. `Product` 저장 후 child를 다시 참조하여 저장

권장 방식은 1번이다.

### 9.2 연결 시점

FK 연결은 child entity 생성 직후 수행한다.

즉, 다음 순서를 따른다.

1. `Product` 생성
2. child entity 생성
3. child entity 내부 `product` 필드에 같은 `Product` 객체 할당
4. `Product`의 컬렉션에 child 추가

### 9.3 저장 일관성

이 방식의 장점:

- child가 부모 없이 저장되는 문제를 막는다.
- 한 트랜잭션 안에서 일관성을 유지한다.
- `cascade = ALL` 설정을 활용할 수 있다.

---

## 10. 조회 모델

이 유스케이스의 최종 목표는 저장뿐 아니라 조회 가능 상태를 만드는 것이다.

조회 기준은 우선 `parentAsin`이다.

조회 대상:

- `Product`
- 필요한 경우 child entity 포함 조회

권장 조회 시나리오:

- `findByParentAsin(String parentAsin)`
- 상세 조회 시 child collection 함께 로딩

---

## 11. 트랜잭션 설계

### 11.1 단건 처리 트랜잭션

JSON line 1개를 처리하는 메서드는 하나의 트랜잭션으로 감싼다.

이유:

- root와 child 저장의 원자성 확보
- 중간 실패 시 롤백 가능

### 11.2 실패 시 정책

다음 중 하나로 정의해야 한다.

- 전체 롤백
- 현재 라인만 스킵하고 상위 호출자에게 실패 반환

초기 설계에서는 전체 롤백이 단순하고 예측 가능하다.

---

## 12. 오류 처리

### 12.1 파싱 실패

- JSON 포맷 오류 발생 시 저장하지 않는다.
- 파싱 실패 원인을 남긴다.

### 12.2 필수값 누락

- `parent_asin` 누락: 저장하지 않는다.
- `title` 누락: 저장하지 않는다.
- `main_category` 누락: `UNKNOWN` 처리 후 저장 가능 여부를 정책으로 결정한다.

### 12.3 자식 데이터 누락

- 배열이 비어 있으면 빈 컬렉션으로 둔다.
- `null`이면 빈 컬렉션 또는 생략으로 처리한다.

### 12.4 중복 데이터

- `parentAsin`은 유니크 키로 취급한다.
- 중복 입력에 대해서는 upsert가 아니라면 저장 실패로 본다.

---

## 13. 테스트 관점

설계 기준 테스트는 다음을 검증해야 한다.

1. JSON line 1개가 정상 저장되는가
2. 저장 후 `parentAsin`으로 조회되는가
3. child entity의 FK가 같은 `Product`를 참조하는가
4. 배열 필드 순서가 유지되는가
5. null/빈 배열 입력에서 예외 없이 동작하는가
6. 필수 필드 누락 시 저장이 차단되는가

---

## 14. 향후 확장 포인트

이 문서의 1건 처리 설계를 기반으로 다음 단계 확장이 가능하다.

- JSONL 파일 전체 스트리밍
- chunk 기반 batch insert
- 실패 라인 별도 보관
- 재처리 큐
- insert 통계 저장
- duplicate skip 정책

---

## 15. 결론

`BulkUploadService`는 JSON line 1개를 받아서:

1. 파싱하고
2. 루트 엔티티를 만들고
3. child entity를 생성하고
4. FK를 연결하고
5. 한 번에 저장하는

단건 insert 오케스트레이터로 정의한다.

이 문서의 기준을 먼저 고정한 뒤 구현을 진행하면, 이후 batch 처리와 파일 스트리밍까지 자연스럽게 확장할 수 있다.
