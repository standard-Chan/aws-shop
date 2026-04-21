# Category별 Product Cursor 조회 API RED 문서

**작성일**: 2026-04-21  
**대상**: `GET /api/products/category` category cursor pagination API  
**참고 설계**: `GET_PRODUCTS_BY_CATEGORY_CURSOR_API_DESIGN.md`  
**단계**: RED

---

## 0. 주요 테스트 목록

RED 단계에서 우선 작성할 주요 테스트는 다음이다.

1. `mainCategory`로 상품을 필터링한다.
2. `size` 기본값은 `20`이다.
3. `size` 최소값은 `1`, 최대값은 `100`이다.
4. `averageRating=true`이면 `average_rating DESC, id ASC`로 조회한다.
5. `ratingNumber=true`이면 `rating_number DESC, id ASC`로 조회한다.
6. `averageRating`과 `ratingNumber`가 모두 true이면 `averageRating`을 우선한다.
7. 정렬 선택값이 없으면 `averageRating`을 기본 정렬로 사용한다.
8. 다음 페이지 요청은 `cursorId + cursorAverageRating` 또는 `cursorId + cursorRatingNumber`를 사용한다.
9. `size + 1`개 조회 결과로 `hasNext`를 계산하고, 응답은 최대 `size`개만 반환한다.
10. `nextCursor`는 응답 마지막 상품 기준으로 만든다.
11. `cursorId`가 존재하지 않으면 `400 Bad Request`를 반환한다.
12. `cursorId`가 요청한 `mainCategory`와 다른 상품이면 `400 Bad Request`를 반환한다.
13. `mainCategory`는 enum name과 하이픈 연결 표시값을 허용한다.
14. 하이픈 연결 category 값 변환은 `MainCategory` enum 테스트로 검증한다.
15. Repository 테스트는 category 조건, 정렬, cursor 이후 데이터 조회 결과에 집중한다.
16. 테스트는 Controller, Service, Domain Enum, Repository 단위로 분리해서 작성한다.

---

## 0.1 파일별 테스트 요약

### ProductControllerGetProductsByCategoryTest

Controller 테스트는 HTTP 요청/응답과 request parameter 전달만 검증한다.

- `GET /api/products/category` 요청을 기존 `ProductController`에서 받는다.
- `size` 생략 시 기본값 `20`을 service에 전달한다.
- `size = 101`은 `400 Bad Request`다.
- `mainCategory`, cursor 값, 정렬 선택값을 기존 `ProductReadService`에 전달한다.
- category 문자열 변환은 controller에서 처리하지 않는다.

### ProductReadServiceGetProductsByCategoryTest

Service 테스트는 정렬 선택, cursor 검증, 응답 조립, 예외 처리를 검증한다.

- `averageRating` 정렬 repository 메서드를 선택한다.
- `ratingNumber` 정렬 repository 메서드를 선택한다.
- 둘 다 true이면 `averageRating`을 우선한다.
- 둘 다 false이면 `averageRating`을 기본 정렬로 사용한다.
- invalid category는 `400 Bad Request`다.
- cursor 일부 누락, 존재하지 않는 cursor id, 다른 category cursor id는 `400 Bad Request`다.
- `size + 1`, `hasNext`, `nextCursor`를 계산한다.

### MainCategoryTest

Domain enum 테스트는 query parameter 문자열을 enum으로 변환하는 규칙만 검증한다.

- `Gift-Cards`처럼 공백 대신 `-`로 연결된 category 값을 `MainCategory`로 변환한다.
- 변환 메서드는 `MainCategory` enum 내부에 둔다.

### ProductRepositoryFindCategoryProductSummariesTest

Repository 테스트는 fixture 데이터를 DB에 저장한 뒤 native query 결과를 검증한다.

- category 필터링을 검증한다.
- `average_rating DESC, id ASC` 정렬을 검증한다.
- `rating_number DESC, id ASC` 정렬을 검증한다.
- cursor 조건으로 다음 페이지를 조회한다.
- repository는 예외 처리를 검증하지 않는다.
- repository는 문자열 category 변환을 검증하지 않는다.

### ProductRepositoryFindProductSummariesTest

기존 전체 목록 repository 테스트는 대표 image 조회 정책을 검증한다.

- `MAIN` 이미지 우선 선택을 검증한다.
- `MAIN` 이미지가 없으면 `ProductImage.id ASC` 첫 번째 이미지를 검증한다.
- 이미지가 없으면 image 필드가 null인지 검증한다.
- product별 대표 image가 1장만 선택되는지 검증한다.

---

## 1. 기능 분석

### 1.1 핵심 기능

Category별 Product 목록 조회 API의 핵심 기능은 다음이다.

- `GET /api/products/category` 요청을 처리한다.
- 기존 `ProductController`에 endpoint를 추가한다.
- 기존 `ProductReadService`에 category 조회 메서드를 추가한다.
- `mainCategory`, `size`, cursor 값, 정렬 선택 query parameter를 받는다.
- `mainCategory`로 상품을 필터링한다.
- `averageRating` 또는 `ratingNumber` 중 하나를 정렬 기준으로 선택한다.
- 정렬 기준이 없으면 `averageRating`을 기본 정렬로 사용한다.
- 둘 다 요청되면 `averageRating`을 우선한다.
- DB에는 `size + 1`개를 요청한다.
- 응답에는 최대 `size`개만 담는다.
- 조회 결과가 `size + 1`개이면 `hasNext = true`로 반환한다.
- 빈 결과는 `products = []`, `nextCursor = null`, `hasNext = false`로 반환한다.

### 1.2 Category 변환 기능

`mainCategory` query parameter는 다음 형식을 허용한다.

- `MainCategory` enum name
- 공백 대신 `-`로 연결된 표시값

예:

- `GIFT_CARDS`
- `HANDMADE_PRODUCTS`
- `SUBSCRIPTION_BOXES`
- `Gift-Cards`
- `Subscription-Boxes`

알 수 없는 category 값은 `400 Bad Request`로 처리한다.

### 1.3 정렬 및 cursor 기능

정렬 기준별 cursor 정책은 다음이다.

`averageRating` 정렬:

- 정렬: `average_rating DESC, id ASC`
- cursor: `cursorId`, `cursorAverageRating`
- 다음 페이지 조건: `average_rating < cursorAverageRating OR (average_rating = cursorAverageRating AND id > cursorId)`

`ratingNumber` 정렬:

- 정렬: `rating_number DESC, id ASC`
- cursor: `cursorId`, `cursorRatingNumber`
- 다음 페이지 조건: `rating_number < cursorRatingNumber OR (rating_number = cursorRatingNumber AND id > cursorId)`

cursor 검증:

- cursor 값이 일부만 있으면 `400 Bad Request`다.
- `cursorId`가 존재하지 않으면 `400 Bad Request`다.
- `cursorId`가 요청한 `mainCategory`와 다른 상품이면 `400 Bad Request`다.

### 1.4 대표 이미지 기능

목록 API는 상품별 이미지 전체가 아니라 대표 이미지 1장만 반환한다.

대표 이미지 선택 기준:

1. `variant = 'MAIN'` 이미지가 있으면 우선 선택한다.
2. `MAIN` 이미지가 없으면 `ProductImage.id ASC` 기준 첫 번째 이미지를 선택한다.
3. 이미지가 없으면 `image = null`을 반환한다.

조회 방식:

- JPQL fetch join을 사용하지 않는다.
- Product 목록과 대표 image를 native query 1번으로 함께 조회한다.
- `ROW_NUMBER()`를 사용한다.
- `ROW_NUMBER()` 정렬 조건은 `MAIN` 우선, 이후 `ProductImage.id ASC`다.

### 1.5 부가 기능

- `size` 기본값은 `20`이다.
- `size` 최소값은 `1`이다.
- `size` 최대값은 `100`이다.
- `size`가 유효하지 않으면 `400 Bad Request`를 반환한다.
- 응답 cursor는 `nextCursor` 객체로 반환한다.
- `averageRating` 정렬 응답 cursor는 `id`, `averageRating`을 포함하고 `ratingNumber = null`이다.
- `ratingNumber` 정렬 응답 cursor는 `id`, `ratingNumber`를 포함하고 `averageRating = null`이다.
- 정렬 기준값이 null인 상품은 조회 대상에서 제외한다.

### 1.6 비범위

RED 단계에서는 다음을 테스트하지 않는다.

- 실제 성능 측정
- DB index 튜닝
- API 문서 자동화
- 프론트엔드 연동
- 인증/인가
- 상품 상세 조회
- 검색어 기반 조회
- MySQL 실 DB 통합 테스트

---

## 2. 테스트 케이스 목록

| ID | 구분 | 테스트 대상 | 조건 | 기대 결과 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| C-001 | 성공 | Controller | `size` 생략 | 기본값 `20`으로 service를 호출한다 | 높음 |
| C-002 | 성공 | Controller | `mainCategory`, cursor, 정렬 parameter 전달 | service에 동일한 값을 전달한다 | 높음 |
| C-003 | 실패 | Controller | `size = 101` | `400 Bad Request` | 높음 |
| S-001 | 성공 | Service | `averageRating=true` | averageRating repository 메서드를 호출한다 | 높음 |
| S-002 | 성공 | Service | `ratingNumber=true` | ratingNumber repository 메서드를 호출한다 | 높음 |
| S-003 | 성공 | Service | `averageRating=true`, `ratingNumber=true` | averageRating을 우선한다 | 높음 |
| S-004 | 성공 | Service | 정렬 선택값 없음 | averageRating을 기본 정렬로 사용한다 | 높음 |
| S-005 | 실패 | Service | invalid category | `400 Bad Request` | 높음 |
| S-006 | 실패 | Service | `cursorId`만 있고 정렬 cursor 값 없음 | `400 Bad Request` | 높음 |
| S-007 | 실패 | Service | 정렬 cursor 값만 있고 `cursorId` 없음 | `400 Bad Request` | 높음 |
| S-008 | 실패 | Service | `cursorId`가 존재하지 않음 | `400 Bad Request` | 높음 |
| S-009 | 실패 | Service | `cursorId`의 category가 요청 category와 다름 | `400 Bad Request` | 높음 |
| S-010 | 성공 | Service | averageRating 정렬 결과 존재 | `nextCursor.id`, `nextCursor.averageRating` 반환 | 높음 |
| S-011 | 성공 | Service | ratingNumber 정렬 결과 존재 | `nextCursor.id`, `nextCursor.ratingNumber` 반환 | 높음 |
| E-001 | 성공 | Domain Enum | `Gift-Cards` query param | `MainCategory.GIFT_CARDS`로 변환한다 | 높음 |
| R-001 | 성공 | Repository | category 조회 | 요청 category 상품만 조회한다 | 높음 |
| R-002 | 성공 | Repository | averageRating 첫 페이지 | `average_rating DESC, id ASC`로 조회한다 | 높음 |
| R-003 | 성공 | Repository | averageRating 다음 페이지 | `cursorAverageRating`, `cursorId` 이후 상품을 조회한다 | 높음 |
| R-004 | 성공 | Repository | ratingNumber 첫 페이지 | `rating_number DESC, id ASC`로 조회한다 | 높음 |
| R-005 | 성공 | Repository | ratingNumber 다음 페이지 | `cursorRatingNumber`, `cursorId` 이후 상품을 조회한다 | 높음 |
| RI-001 | 성공 | Repository | `MAIN` 이미지 존재 | `MAIN` 이미지를 대표 image로 조회한다 | 높음 |
| RI-002 | Edge | Repository | `MAIN` 이미지 없음 | `ProductImage.id ASC` 첫 번째 이미지를 대표 image로 조회한다 | 높음 |
| RI-003 | Edge | Repository | 이미지 없음 | 상품은 반환되고 image 필드는 null이다 | 높음 |
| RI-004 | 성공 | Repository | 여러 이미지 존재 | product별 대표 image 1장만 선택한다 | 높음 |

---

## 3. RED 테스트 작성 계획

테스트는 Controller, Service, Domain Enum, Repository 단위로 분리해서 작성한다.

- Controller 테스트: HTTP 요청/응답, parameter 기본값, validation, service 호출만 검증한다.
- Service 테스트: 정렬 선택, cursor 검증, `size + 1`, `hasNext`, `nextCursor`, DTO 변환, 예외 처리를 검증한다.
- Domain Enum 테스트: query parameter 문자열을 `MainCategory`로 변환하는 규칙을 검증한다.
- Repository 테스트: 실제 DB에 데이터를 넣고 native query의 category 조건, 정렬, cursor 이후 조회 결과를 검증한다.

Repository 테스트는 fixture JSONL을 기존 `BulkInsertService`로 저장한 뒤 조회한다. 테스트 데이터 생성 책임은 repository 테스트의 `Given` 단계에 둔다.

### 3.1 Controller 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/controller/ProductControllerGetProductsByCategoryTest.java
```

테스트 메서드 후보:

```java
should_call_service_with_default_size_when_size_is_omitted
should_call_service_with_category_cursor_and_sort_parameters_when_request_is_valid
should_return_bad_request_when_size_is_greater_than_max
```

검증 범위:

- 기존 `ProductController`가 `/api/products/category` 요청을 받는다.
- `size` 생략 시 `20`을 service에 전달한다.
- `mainCategory`, `cursorId`, `cursorAverageRating`, `cursorRatingNumber`, `averageRating`, `ratingNumber`를 service에 전달한다.
- `size = 101`은 `400 Bad Request`다.

RED 의도:

- 아직 category endpoint가 없거나 service 메서드 시그니처가 없으면 컴파일 실패 또는 테스트 실패가 발생한다.
- controller는 category 문자열 변환을 직접 하지 않고 service에 위임한다.

### 3.2 Service 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/service/productread/ProductReadServiceGetProductsByCategoryTest.java
```

테스트 메서드 후보:

```java
should_call_average_rating_repository_when_average_rating_sort_is_requested
should_call_rating_number_repository_when_rating_number_sort_is_requested
should_prioritize_average_rating_when_both_sort_options_are_true
should_use_average_rating_sort_when_sort_options_are_absent
should_throw_bad_request_when_category_is_invalid
should_throw_bad_request_when_cursor_id_exists_without_sort_cursor_value
should_throw_bad_request_when_sort_cursor_value_exists_without_cursor_id
should_throw_bad_request_when_cursor_id_does_not_exist
should_throw_bad_request_when_cursor_category_is_different
should_return_average_rating_cursor_when_average_rating_sort_has_next
should_return_rating_number_cursor_when_rating_number_sort_has_next
```

검증 범위:

- service가 정렬 기준을 결정한다.
- service가 cursor 값 조합을 검증한다.
- service가 cursor id 존재 여부를 repository로 확인한다.
- service가 cursor id와 category 일치 여부를 repository로 확인한다.
- service가 `size + 1`을 repository limit으로 전달한다.
- service가 repository 결과에서 앞의 `size`개만 응답에 담는다.
- service가 `hasNext`를 계산한다.
- service가 `nextCursor`를 계산한다.
- service가 native projection을 response DTO로 변환한다.

RED 의도:

- `ProductReadService#getProductsByCategory`
- `ProductCategoryCursorResponse`
- `CategoryCursor`
- `ProductCategorySortType`
- cursor 검증 예외

위 클래스와 메서드는 현재 구현되지 않았거나 불완전할 수 있으므로 테스트는 컴파일 실패 또는 실패 상태가 될 수 있다.

### 3.3 Domain Enum 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/domain/MainCategoryTest.java
```

테스트 메서드 후보:

```java
should_convert_hyphenated_category_when_category_is_query_param_value
```

검증 범위:

- query parameter에서 공백 대신 `-`로 전달된 category 값을 `MainCategory`로 변환한다.
- 변환 메서드는 `MainCategory` enum 내부에 둔다.

RED 의도:

- `MainCategory#fromQueryParam`
- 하이픈 연결 query parameter 변환 규칙

위 메서드는 아직 구현되지 않았으므로 컴파일 실패 또는 실패 상태가 될 수 있다.

### 3.4 Repository 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/repository/ProductRepositoryFindCategoryProductSummariesTest.java
```

테스트 메서드 후보:

```java
should_find_products_by_category_when_category_exists
should_find_products_ordered_by_average_rating_desc_and_id_asc_when_cursor_is_null
should_find_next_products_by_average_rating_cursor_when_cursor_exists
should_find_products_ordered_by_rating_number_desc_and_id_asc_when_cursor_is_null
should_find_next_products_by_rating_number_cursor_when_cursor_exists
```

데이터 준비 방식:

```text
JSONL fixture
  -> BulkInsertService로 DB 저장
  -> ProductRepository native query 호출
  -> 조회 결과 검증
```

테스트 데이터 원칙:

- 사용자가 제공한 Amazon product JSON 포맷을 기준으로 fixture를 만든다.
- cursor 정렬 검증을 위해 같은 category에 상품을 충분히 추가한다.
- 같은 `averageRating`을 가진 상품을 여러 개 넣어 `id ASC` tie-breaker를 검증한다.
- 같은 `ratingNumber`를 가진 상품을 여러 개 넣어 `id ASC` tie-breaker를 검증한다.
- `MAIN` 이미지가 있는 상품, `MAIN` 이미지가 없는 상품, 이미지가 없는 상품을 모두 포함한다.
- 각 테스트에서 데이터 수정이나 삭제는 발생하지 않는다.
- 모든 테스트 시작 전에 한 번만 데이터를 넣어서 진행한다.

검증 범위:

- native query가 category 조건을 적용한다.
- native query가 `average_rating DESC, id ASC` 정렬을 보장한다.
- native query가 averageRating cursor 조건을 적용한다.
- native query가 `rating_number DESC, id ASC` 정렬을 보장한다.
- native query가 ratingNumber cursor 조건을 적용한다.
- repository는 exception을 던지는 책임을 갖지 않는다.
- repository는 query parameter 문자열을 enum으로 변환하지 않는다.
- repository 테스트는 mock을 사용하지 않고 실제 DB 저장 결과를 조회한다.

RED 의도:

- category native query 메서드가 아직 없으므로 컴파일 실패가 발생할 수 있다.
- 현재 RED 단계의 repository 테스트는 H2에서 진행한다.
- 대표 image 선택 정책은 기존 `ProductRepositoryFindProductSummariesTest`에서 별도로 검증한다.
- 실제 MySQL 연동 테스트는 시도할 수 있으나, 속도 문제와 테이블 초기화 문제로 RED 단계에 도입하기 불편하다.
- 따라서 MySQL 기반 native query 통합 테스트는 추후 별도 통합 테스트 단계에서 구현한다.
- `BulkInsertService` 호출 방식이 테스트에서 바로 사용하기 어렵다면 GREEN 전에 테스트 데이터 적재 helper 설계를 다시 확인한다.

---

## 4. RED 단계 작성 규칙

테스트 코드를 작성할 때는 `docs/TDD/red.md` 기준을 따른다.

- 구현 코드는 작성하지 않는다.
- 테스트가 컴파일은 되지만 반드시 실패하도록 작성한다.
- 존재하지 않는 클래스나 메서드는 그대로 직접 참조한다.
- 존재하지 않는 클래스를 우회하려고 리플렉션, 래퍼 메서드, 테스트용 팩토리 메서드를 만들지 않는다.
- 테스트 메서드명은 `should_[기대결과]_when_[조건]` 형식을 사용한다.
- `Given / When / Then` 주석을 작성한다.
- `@DisplayName`에는 한국어로 테스트 의도를 적는다.
- assertion은 AssertJ `assertThat`을 사용한다.

---

## 5. 작성 순서 제안

RED 테스트 코드는 아래 순서로 작성한다.

1. Controller 테스트로 API parameter 계약을 먼저 고정한다.
2. Service 테스트로 정렬 선택, cursor 검증, 응답 cursor 생성을 고정한다.
3. Domain Enum 테스트로 query parameter category 변환 규칙을 고정한다.
4. Repository 테스트로 native query 정렬과 cursor 조건을 고정한다.

이 순서로 작성하면 API 계약부터 DB 조회 조건까지 단계별로 실패 지점이 명확해진다.
