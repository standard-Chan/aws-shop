# Product 목록 Cursor 조회 API RED 문서

**작성일**: 2026-04-21  
**대상**: `GET /api/products` cursor pagination API  
**참고 설계**: `GET_ALL_PRODUCTS_CURSOR_API_DESIGN.md`  
**단계**: RED

---

## 0. 주요 테스트 목록

RED 단계에서 우선 작성할 주요 테스트는 다음이다.

1. `cursor = null`이면 첫 페이지를 `id ASC` 기준으로 조회한다.
2. `cursor`가 있으면 `id > cursor` 조건으로 다음 페이지를 조회한다.
3. `size + 1`개 조회 결과로 `hasNext`를 계산하고, 응답은 최대 `size`개만 반환한다.
4. `nextCursorId`는 응답에 포함된 마지막 Product id로 반환한다.
5. 대표 이미지는 `MAIN`을 우선 선택하고, 없으면 `ProductImage.id ASC` 첫 번째 이미지를 선택한다.
6. 이미지가 없는 상품은 `image = null`로 반환한다.
7. native query는 Product 목록과 대표 image를 한 번에 조회한다.
8. 잘못된 `size`, `cursor` 요청은 `400 Bad Request`로 거절한다.
9. Repository 테스트는 JSONL fixture를 `BulkInsertService`로 DB에 저장한 뒤 native query로 조회한다.
10. 테스트는 Controller, Service, Repository 단위로 분리해서 작성한다.

---

## 1. 기능 분석

### 1.1 핵심 기능

Product 목록 조회 API의 핵심 기능은 다음이다.

- `GET /api/products` 요청을 처리한다.
- `size`, `cursor` query parameter를 받는다.
- `Product.id ASC` 기준으로 정렬한다.
- `cursor`가 없으면 첫 페이지를 조회한다.
- `cursor`가 있으면 `id > cursor` 조건으로 조회한다.
- DB에는 `size + 1`개를 요청한다.
- 응답에는 최대 `size`개만 담는다.
- 조회 결과가 `size + 1`개이면 `hasNext = true`로 반환한다.
- `nextCursorId`는 응답에 포함된 마지막 Product id로 반환한다.
- 빈 결과는 `products = []`, `nextCursorId = null`, `hasNext = false`로 반환한다.

### 1.2 대표 이미지 기능

목록 API는 상품별 이미지 전체가 아니라 대표 이미지 1장만 반환한다.

대표 이미지 선택 기준:

1. `variant = 'MAIN'` 이미지가 있으면 우선 선택한다.
2. `MAIN` 이미지가 없으면 `ProductImage.id ASC` 기준 첫 번째 이미지를 선택한다.
3. 이미지가 없으면 `image = null`을 반환한다.

조회 방식:

- JPQL fetch join을 사용하지 않는다.
- Product 목록과 대표 image를 native query 1번으로 함께 조회한다.
- MySQL 8 이상 `ROW_NUMBER()`를 사용한다.
- `ROW_NUMBER()` 정렬 조건은 `MAIN` 우선, 이후 `ProductImage.id ASC`다.

### 1.3 부가 기능

- `size` 기본값은 `20`이다.
- `size` 최소값은 `1`이다.
- `size` 최대값은 `100`이다.
- `cursor`는 양수만 허용한다.
- `size` 또는 `cursor`가 유효하지 않으면 `400 Bad Request`를 반환한다.
- 응답 필드명은 `averageRating`을 사용한다.
- 대표 이미지 응답 필드명은 `image` 단수로 사용한다.

### 1.4 비범위

RED 단계에서는 다음을 테스트하지 않는다.

- 실제 성능 측정
- DB index 튜닝
- API 문서 자동화
- 프론트엔드 연동
- 인증/인가
- 상품 상세 조회
- 검색/필터/정렬 옵션 추가

---

## 2. 테스트 케이스 목록

| ID | 구분 | 테스트 대상 | 조건 | 기대 결과 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| P-001 | 성공 | Service | `cursor = null`, `size = 3` | 첫 페이지를 조회하고 최대 3개를 반환한다 | 높음 |
| P-002 | 성공 | Service | `cursor = 12`, `size = 3` | `id > 12` 기준으로 다음 페이지를 반환한다 | 높음 |
| P-003 | 성공 | Service | repository가 `size + 1`개 반환 | 응답은 `size`개만 담고 `hasNext = true` | 높음 |
| P-004 | 성공 | Service | repository가 `size`개 이하 반환 | 전체를 응답하고 `hasNext = false` | 높음 |
| P-005 | 성공 | Service | 응답 상품이 존재 | 마지막 상품 id를 `nextCursorId`로 반환한다 | 높음 |
| P-006 | Edge | Service | 조회 결과 없음 | `products = []`, `nextCursorId = null`, `hasNext = false` | 높음 |
| P-007 | 성공 | Repository | 상품에 `MAIN` 이미지 존재 | `MAIN` 이미지를 대표 image로 조회한다 | 높음 |
| P-008 | Edge | Repository | 상품에 `MAIN` 이미지 없음 | `ProductImage.id ASC` 첫 번째 이미지를 대표 image로 조회한다 | 높음 |
| P-009 | Edge | Repository | 상품에 이미지 없음 | 상품은 반환되고 image 필드는 null이다 | 높음 |
| P-010 | 성공 | Repository | 여러 이미지 존재 | `ROW_NUMBER()` 우선순위가 product별로 1장만 선택한다 | 높음 |
| P-011 | 실패 | Controller | `size = 0` | `400 Bad Request` | 중간 |
| P-012 | 실패 | Controller | `size = 101` | `400 Bad Request` | 중간 |
| P-015 | 성공 | Controller | `size` 생략 | 기본값 `20`으로 service를 호출한다 | 중간 |
| P-016 | 성공 | DTO | native projection의 image 필드가 모두 null | `ProductImageResponse`를 만들지 않고 `image = null` | 중간 |

---

## 3. RED 테스트 작성 계획

테스트는 Controller, Service, Repository 단위로 분리해서 작성한다.

- Controller 테스트: HTTP 요청/응답, parameter 기본값, validation만 검증한다.
- Service 테스트: cursor 계산, `size + 1`, `hasNext`, `nextCursorId`, DTO 변환만 검증한다.
- Repository 테스트: 실제 DB에 데이터를 넣고 native query 결과를 검증한다.

Repository 테스트는 fixture JSONL을 기존 `BulkInsertService`로 저장한 뒤 조회한다. 테스트 데이터 생성 책임은 repository 테스트의 `Given` 단계에 둔다.

### 3.1 Service 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/service/productread/ProductReadServiceTest.java
```

테스트 메서드 후보:

```java
should_return_first_page_when_cursor_is_null
should_return_next_page_when_cursor_exists
should_return_has_next_true_when_rows_are_more_than_size
should_return_has_next_false_when_rows_are_not_more_than_size
should_return_last_product_id_as_next_cursor_id_when_products_exist
should_return_empty_response_when_products_do_not_exist
should_return_null_image_when_projection_has_no_image
```

검증 범위:

- service가 `size + 1`을 repository limit으로 전달한다.
- service가 repository 결과에서 앞의 `size`개만 응답에 담는다.
- service가 `hasNext`를 계산한다.
- service가 `nextCursorId`를 계산한다.
- service가 native projection을 response DTO로 변환한다.
- image 관련 projection 값이 없으면 `image = null`로 변환한다.

RED 의도:

- `ProductReadService`
- `ProductCursorResponse`
- `ProductSummaryResponse`
- `ProductImageResponse`
- `ProductSummaryNativeProjection`
- `findProductSummaries`

위 클래스와 메서드는 현재 구현되지 않았거나 불완전할 수 있으므로 테스트는 컴파일 실패 또는 실패 상태가 될 수 있다.

### 3.2 Repository 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/repository/ProductRepositoryCursorQueryTest.java
```

테스트 메서드 후보:

```java
should_find_products_ordered_by_id_asc_when_cursor_is_null
should_find_products_after_cursor_when_cursor_exists
should_select_main_image_when_main_image_exists
should_select_first_image_by_id_when_main_image_does_not_exist
should_return_product_with_null_image_when_product_has_no_image
should_return_only_one_representative_image_per_product_when_product_has_multiple_images
```

데이터 준비 방식:

```text
JSONL fixture
  -> BulkInsertService로 DB 저장
  -> ProductRepository native query 호출
  -> 조회 결과 검증
```

검증 범위:

- native query가 `id ASC` 정렬을 보장한다.
- native query가 `id > cursor` 조건을 적용한다.
- native query가 `LIMIT size + 1`을 적용한다.
- native query가 `ROW_NUMBER()`로 product별 대표 이미지 1장만 선택한다.
- `MAIN` 이미지가 우선 선택된다.
- `MAIN`이 없으면 `ProductImage.id ASC` 첫 번째 이미지가 선택된다.
- 이미지가 없어도 Product row는 `LEFT JOIN`으로 유지된다.
- repository 테스트는 mock을 사용하지 않고 실제 DB 저장 결과를 조회한다.
- fixture row 수가 cursor 테스트에 부족하면 테스트용 Product JSONL을 추가로 만든다.

RED 의도:

- native query 메서드가 아직 없으므로 컴파일 실패가 발생할 수 있다.
- 현재 RED 단계의 repository 테스트는 H2에서 진행한다.
- 대표 이미지 선택 native query는 MySQL 기능 의존성이 있으므로 H2에서 완전한 통합 검증이 어려울 수 있다.
- 실제 MySQL 연동 테스트는 시도할 수 있으나, 속도 문제와 테이블 초기화 문제로 RED 단계에 도입하기 불편하다.
- 따라서 MySQL 기반 native query 통합 테스트는 추후 별도 통합 테스트 단계에서 구현한다.
- `BulkInsertService` 호출 방식이 테스트에서 바로 사용하기 어렵다면 GREEN 전에 테스트 데이터 적재 helper 설계를 다시 확인한다.

### 3.3 Controller 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/controller/ProductControllerTest.java
```

테스트 메서드 후보:

```java
should_call_service_with_default_size_when_size_is_omitted
should_call_service_with_cursor_when_cursor_exists
should_return_bad_request_when_size_is_zero
should_return_bad_request_when_size_is_greater_than_max
```

검증 범위:

- `GET /api/products` endpoint가 존재한다.
- `size` 생략 시 기본값 `20`이 적용된다.
- `cursor`가 있으면 service로 전달된다.
- 유효하지 않은 request parameter는 `400 Bad Request`를 반환한다.

RED 의도:

- 현재 `ProductController`는 비어 있으므로 controller 테스트는 컴파일 또는 요청 매핑 단계에서 실패해야 한다.

---

## 4. 우선 작성 테스트

RED 단계에서 한 번에 모든 테스트를 작성하면 구현 범위가 커질 수 있다. 우선순위는 다음 순서로 둔다.

1. Service cursor pagination 테스트
2. Service DTO 변환 테스트
3. Repository native query 대표 이미지 테스트
4. Controller parameter 검증 테스트

첫 RED 코드 작성 시 추천 범위:

| 순서 | 테스트 |
| --- | --- |
| 1 | `should_return_first_page_when_cursor_is_null` |
| 2 | `should_return_next_page_when_cursor_exists` |
| 3 | `should_return_has_next_true_when_rows_are_more_than_size` |
| 4 | `should_return_last_product_id_as_next_cursor_id_when_products_exist` |
| 5 | `should_select_main_image_when_main_image_exists` |
| 6 | `should_select_first_image_by_id_when_main_image_does_not_exist` |
| 7 | `should_return_bad_request_when_size_is_zero` |

---

## 5. Repository 테스트 Fixture

Repository 테스트는 실제 JSON 포맷을 기반으로 데이터를 저장한다. 제공된 상품 JSON은 전체 필드를 가진 실제 데이터 형태이므로, 테스트에서는 필요한 필드만 유지한 축약 JSONL을 사용해도 된다.

### 5.1 기본 fixture

아래 fixture는 `MAIN` 이미지가 있는 기본 상품들이다.

```jsonl
{"main_category":"Gift Cards","title":"Amazon.com Gift Card in Gift Tag (Various Designs)","average_rating":4.8,"rating_number":1006,"price":null,"images":[{"thumb":"gift-main-thumb","large":"gift-main-large","variant":"MAIN","hi_res":"gift-main-hires"},{"thumb":"gift-pt01-thumb","large":"gift-pt01-large","variant":"PT01","hi_res":"gift-pt01-hires"}],"store":"Amazon","parent_asin":"B06ZXTKYHN","features":[],"description":[],"categories":["Gift Cards"],"details":{},"videos":[],"bought_together":null}
{"main_category":"SUBSCRIPTION BOXES","title":"Loved Again Media - Movie Subscription Box - 10 DVD Box - Pick Your Genres","average_rating":4.1,"rating_number":75,"price":null,"images":[{"thumb":"movie-main-thumb","large":"movie-main-large","variant":"MAIN","hi_res":"movie-main-hires"},{"thumb":"movie-pt11-thumb","large":"movie-pt11-large","variant":"PT11","hi_res":"movie-pt11-hires"}],"store":"Loved Again Media","parent_asin":"B08W5BSH6V","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
{"main_category":"Handmade","title":"Daisy Keychain Wristlet Gray Fabric Key fob Lanyard","average_rating":4.5,"rating_number":12,"price":null,"images":[{"thumb":"daisy-main-thumb","large":"daisy-main-large","variant":"MAIN","hi_res":null},{"thumb":"daisy-pt01-thumb","large":"daisy-pt01-large","variant":"PT01","hi_res":null}],"store":"Generic","parent_asin":"B07NTK7T5P","features":[],"description":[],"categories":["Handmade Products"],"details":{},"videos":[],"bought_together":null}
{"main_category":"Handmade","title":"Silver Triangle Earrings with Chevron Pattern","average_rating":5.0,"rating_number":1,"price":null,"images":[{"thumb":"silver-main-thumb","large":"silver-main-large","variant":"MAIN","hi_res":"silver-main-hires"},{"thumb":"silver-pt01-thumb","large":"silver-pt01-large","variant":"PT01","hi_res":"silver-pt01-hires"}],"store":"Zoë Noelle Designs","parent_asin":"B01HYNE114","features":[],"description":[],"categories":["Handmade Products"],"details":{},"videos":[],"bought_together":null}
```

### 5.2 Edge fixture

대표 이미지 fallback과 null image를 검증하기 위해 아래 데이터를 추가한다.

```jsonl
{"main_category":"Handmade","title":"No Main Image Product","average_rating":3.9,"rating_number":8,"price":12.50,"images":[{"thumb":"no-main-first-thumb","large":"no-main-first-large","variant":"PT01","hi_res":"no-main-first-hires"},{"thumb":"no-main-second-thumb","large":"no-main-second-large","variant":"PT02","hi_res":"no-main-second-hires"}],"store":"Fixture Store","parent_asin":"TEST_NO_MAIN","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
{"main_category":"Handmade","title":"No Image Product","average_rating":2.0,"rating_number":1,"price":7.00,"images":[],"store":"Fixture Store","parent_asin":"TEST_NO_IMAGE","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
```

### 5.3 Cursor fixture 보강 원칙

제공된 데이터만으로 cursor 흐름을 검증하기 어렵다면 테스트용 상품을 임의로 추가한다.

추가 데이터 원칙:

- `parent_asin`은 중복되지 않아야 한다.
- `title`, `main_category`, `average_rating`, `rating_number`, `store`, `images`만 최소로 유지해도 된다.
- cursor 테스트는 DB가 생성한 `Product.id`를 기준으로 수행한다.
- 테스트에서 id 값을 하드코딩하지 않고, 저장 후 조회된 id 목록을 기준으로 cursor를 선택한다.

예시:

```jsonl
{"main_category":"Handmade","title":"Cursor Fixture 01","average_rating":4.0,"rating_number":10,"price":1.00,"images":[{"thumb":"cursor-01-main-thumb","large":"cursor-01-main-large","variant":"MAIN","hi_res":null}],"store":"Cursor Store","parent_asin":"TEST_CURSOR_01","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
{"main_category":"Handmade","title":"Cursor Fixture 02","average_rating":4.0,"rating_number":10,"price":2.00,"images":[{"thumb":"cursor-02-main-thumb","large":"cursor-02-main-large","variant":"MAIN","hi_res":null}],"store":"Cursor Store","parent_asin":"TEST_CURSOR_02","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
{"main_category":"Handmade","title":"Cursor Fixture 03","average_rating":4.0,"rating_number":10,"price":3.00,"images":[{"thumb":"cursor-03-main-thumb","large":"cursor-03-main-large","variant":"MAIN","hi_res":null}],"store":"Cursor Store","parent_asin":"TEST_CURSOR_03","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
```

### 5.4 BulkInsertService 사용 방침

Repository 테스트의 `Given` 단계에서는 이미 구현된 `BulkInsertService`를 사용해 fixture JSONL을 저장한다.

테스트 의도:

- repository native query를 실제 저장 구조 위에서 검증한다.
- Product와 ProductImage의 FK 연결이 실제 적재 흐름과 동일한 상태에서 검증된다.
- 테스트가 수동 entity 생성 방식에 의존하지 않는다.

주의 사항:

- RED 단계에서는 테스트 문서만 작성한다.
- repository 테스트 데이터는 모든 테스트 시작 전에 한 번만 저장한다.
- 각 repository 테스트에서는 데이터 수정이나 삭제를 수행하지 않는다.
- `parent_asin` unique 제약이 있으므로 같은 테스트 컨텍스트에서 fixture를 중복 저장하지 않는다.
- 테스트 코드 작성 시 `BulkInsertService`의 실제 public method 시그니처를 확인한 뒤 호출한다.
- `BulkInsertService`가 batch size 또는 stream 입력을 요구하면 fixture JSONL을 해당 형식에 맞춰 전달한다.

---

## 6. 테스트 코드 작성 규칙

`/docs/TDD/red.md` 기준을 따른다.

- 구현 코드는 작성하지 않는다.
- 테스트가 컴파일은 되지만 실패하거나, 존재하지 않는 클래스/메서드 참조로 컴파일 실패해도 된다.
- 존재하지 않는 클래스를 우회하기 위해 리플렉션, 래퍼, 팩토리 메서드를 만들지 않는다.
- 테스트 본문에서 대상 클래스와 메서드를 직접 참조한다.
- 테스트 메서드명은 `should_[기대결과]_when_[조건]` 형식을 사용한다.
- `Given / When / Then` 주석을 작성한다.
- `@DisplayName`은 한국어로 테스트 의도를 명시한다.
- assertion은 AssertJ `assertThat`을 사용한다.

---

## 7. 확인 필요 사항

테스트 코드 작성 전에 다음을 확인한다.

1. Repository native query 테스트는 실제 DB 통합 테스트로 작성한다.
2. Repository 테스트 데이터는 `BulkInsertService`로 저장한다.
3. Repository 테스트 데이터는 모든 테스트 시작 전에 한 번만 저장하고, 각 테스트에서는 수정/삭제하지 않는다.
4. Controller validation을 annotation 기반으로 할지 service validation으로 둘지 결정한다.
5. `mainCategory` native projection 문자열을 enum으로 변환하는 실패 케이스는 이번 RED 범위에서 제외한다.
6. 첫 RED 코드 작성 범위를 위 `4. 우선 작성 테스트` 목록으로 진행할지 결정한다.

---

## 8. 계층별 작성할 테스트

### 8.1 Controller 테스트

목표:

- HTTP 요청을 올바르게 받는지 검증한다.
- query parameter 기본값과 validation을 검증한다.
- business logic은 검증하지 않는다.
- `ProductReadService`는 mock 처리한다.

파일:

```text
src/test/java/jeong/awsshop/product/controller/ProductControllerTest.java
```

작성할 테스트:

| 테스트 메서드 | 검증 내용                                                                            |
| --- |----------------------------------------------------------------------------------|
| `should_call_service_with_default_size_when_size_is_omitted` | `GET /api/products` 요청에서 `size` 생략 시 service가 `size = 20`, `cursor = null`로 호출된다 |
| `should_call_service_with_size_and_cursor_when_query_parameters_exist` | `size`, `cursor`가 있으면 service에 그대로 전달된다                                          |
| `should_return_ok_with_cursor_response_when_request_is_valid` | service 응답을 HTTP 200 JSON으로 반환한다                                                 |
| `should_return_bad_request_when_size_is_negative` | `size <= 0`이면 `400 Bad Request`를 반환한다                                            |
| `should_return_bad_request_when_size_is_greater_than_max` | `size >= 101`이면 `400 Bad Request`를 반환한다                                           |

cursor validation 테스트는 작성하지 않는다. cursor는 일반 증가 정수가 아니라 snowflake id이므로 `0`, `1`, `2`, `3`, `4`, `5` 같은 일반 정수 범위를 controller에서 검증하는 테스트는 제외한다.

검증하지 않을 것:

- native query 동작
- 대표 이미지 선택 우선순위
- `hasNext`, `nextCursorId` 계산 세부 로직

### 8.2 Service 테스트

목표:

- cursor pagination 응답 조립을 검증한다.
- repository 호출 parameter를 검증한다.
- native projection을 response DTO로 변환하는 로직을 검증한다.
- repository는 mock 처리한다.

파일:

```text
src/test/java/jeong/awsshop/product/service/productread/ProductReadServiceTest.java
```

작성할 테스트:

| 테스트 메서드 | 검증 내용                                                       |
| --- |-------------------------------------------------------------|
| `should_request_size_plus_one_when_get_products` | service가 repository에 `size + 1`을 limit으로 전달한다               |
| `should_return_first_page_when_cursor_is_null` | `cursor = null`이면 첫 페이지 조회 요청을 repository에 전달한다             |
| `should_return_next_page_when_cursor_exists` | `cursor`가 있으면 해당 cursor를 repository에 전달한다                      |
| `should_return_has_next_true_when_rows_are_more_than_size` | repository 결과가 `size + 1`개이면 응답은 `size`개이고 `hasNext = true`다 |
| `should_return_has_next_false_when_rows_are_not_more_than_size` | repository 결과가 `size`개 이하이면 `hasNext = false`다              |
| `should_return_last_product_id_as_next_cursor_id_when_products_exist` | 응답 상품이 있으면 마지막 상품 id가 `nextCursorId`다                       |
| `should_return_null_next_cursor_id_when_products_do_not_exist` | 응답 상품이 없으면 `nextCursorId = null`이다                          |
| `should_return_empty_products_when_repository_returns_empty_rows` | repository 결과가 비어 있으면 빈 목록 응답을 반환한다                         |
| `should_map_native_projection_to_product_summary_response` | Product native projection 필드를 `ProductSummaryResponse`로 변환한다 |
| `should_return_null_image_when_image_projection_is_null` | image 관련 projection 값이 모두 null이면 `image`는 null 값을 갖는다     |

검증하지 않을 것:

- HTTP status
- query parameter binding
- 실제 SQL 실행 결과
- `ROW_NUMBER()` 동작

### 8.3 Repository 테스트

목표:

- native query가 실제 DB 저장 데이터 기준으로 올바르게 동작하는지 검증한다.
- fixture JSONL을 `BulkInsertService`로 저장한 뒤 조회한다.
- mock을 사용하지 않는다.

파일:

```text
src/test/java/jeong/awsshop/product/repository/ProductRepositoryCursorQueryTest.java
```

작성할 테스트:

| 테스트 메서드 | 검증 내용 |
| --- | --- |
| `should_find_products_ordered_by_id_asc_when_cursor_is_null` | cursor가 없으면 `id ASC` 순서로 상품을 조회한다 |
| `should_find_products_after_cursor_when_cursor_exists` | cursor가 있으면 `id > cursor`인 상품만 조회한다 |
| `should_limit_rows_by_size_plus_one_when_limit_is_given` | repository limit 값만큼 row를 조회한다 |
| `should_select_main_image_when_main_image_exists` | `MAIN` 이미지가 있으면 대표 image로 `MAIN`을 반환한다 |
| `should_select_first_image_by_id_when_main_image_does_not_exist` | `MAIN`이 없으면 `ProductImage.id ASC` 첫 번째 이미지를 반환한다 |
| `should_return_product_with_null_image_when_product_has_no_image` | 이미지가 없어도 Product는 조회되고 image 필드는 null이다 |
| `should_return_only_one_representative_image_per_product_when_product_has_multiple_images` | 이미지가 여러 개여도 Product row는 1개만 반환된다 |
| `should_project_only_required_product_fields_when_find_products` | 응답 projection에 목록 API가 필요한 필드만 매핑된다 |

데이터 준비:

1. 기본 fixture JSONL을 준비한다.
2. edge fixture JSONL을 준비한다.
3. cursor 검증에 row가 부족하면 cursor fixture JSONL을 추가한다.
4. 모든 repository 테스트 시작 전에 `BulkInsertService`를 한 번 호출해 DB에 저장한다.
5. 각 테스트는 저장된 데이터를 수정하거나 삭제하지 않는다.
6. 저장 후 native query를 호출한다.
7. 결과의 id, image variant, image URL, null image 여부를 검증한다.

H2 테스트 범위:

- 우선 RED 단계에서는 H2에서 repository 테스트를 진행한다.
- 대표 이미지 native query는 MySQL `ROW_NUMBER()` 기반이므로 H2에서 실제 운영 쿼리와 완전히 동일하게 검증하기 어렵다.
- 실제 MySQL 연동 테스트는 해볼 수 있으나, 속도 문제와 테이블 초기화 문제로 현재 RED 단계에 도입하지 않는다.
- MySQL 기반 통합 테스트는 추후 별도 단계에서 구현한다는 의도를 테스트 주석에 남긴다.

검증하지 않을 것:

- Controller validation
- Service의 `hasNext` 계산
- Service의 `nextCursorId` 계산

### 8.4 테스트 작성 순서

RED 코드는 다음 순서로 작성한다.

1. Service 테스트를 먼저 작성한다.
2. Repository native query 테스트를 작성한다.
3. Controller 테스트를 작성한다.

이유:

- Service 테스트는 API 응답 정책을 가장 빠르게 고정한다.
- Repository 테스트는 native query와 fixture 저장 흐름을 검증한다.
- Controller 테스트는 endpoint 계약과 validation만 마지막에 고정한다.
