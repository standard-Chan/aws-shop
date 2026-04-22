# Product 상세 조회 API RED 문서

**대상**: `GET /api/products/{id}` 단일 Product 상세 조회 API  
**참고 설계**: `GET_PRODUCT_DETAILS_API_DESIGN.md`  
**단계**: RED

---

## 0. 주요 테스트 목록

RED 단계에서 작성할 테스트는 다음이다.

1. `GET /api/products/{id}`가 `ProductReadService#getProductDetail(id)`를 호출한다.
2. 유효한 id 요청은 Product 본문과 모든 child collection을 JSON으로 반환한다.
3. `id <= 0`이면 `400 Bad Request`를 반환한다.
4. 숫자로 변환할 수 없는 id이면 `400 Bad Request`를 반환한다.
5. Product가 없으면 `404 Not Found`를 반환한다.
6. Service는 Product 본문 projection과 child projection들을 조립해 `ProductDetailResponse`를 만든다.
7. Service는 Product가 없으면 `ProductNotFoundException`을 던진다.
8. child collection이 없으면 null이 아니라 빈 list를 반환한다.
9. `details`가 null 또는 blank이면 빈 map을 반환한다.
10. `details` JSON object는 `Map<String, Object>`로 반환한다.
11. Repository는 Product 본문 projection을 id로 조회한다.
12. Repository는 feature와 description을 index ASC, nulls last, id ASC로 조회한다.
13. Repository는 image를 `MAIN` 우선, id ASC로 조회한다.

---

## 1. 기능 분석

### 1.1 핵심 기능

- Product id로 단일 상품을 조회한다.
- Product 본문 필드 전체를 반환한다.
- Product child collection 전체를 반환한다.
- Product가 없으면 `404 Not Found`를 반환한다.
- child collection은 항상 non-null list로 반환한다.
- `details`는 JSON 문자열이 아니라 JSON object로 반환한다.

### 1.2 부가 기능

- `id`는 양수만 허용한다.
- `/api/products/category`는 기존 category 목록 조회 API로 유지한다.
- child 내부 DB id와 `product_id`는 응답하지 않는다.
- image는 `MAIN` variant를 먼저 반환한다.
- feature와 description은 index null을 뒤로 보낸다.

### 1.3 비범위

- 리뷰 조회
- 재고/배송/장바구니 정보
- 추천 상품 계산
- cache
- 인증/인가

---

## 2. 테스트 케이스 목록

| ID | 구분 | 테스트 대상 | 조건                        | 기대 결과 |
| --- | --- | --- |---------------------------| --- |
| PD-001 | 성공 | Controller | `GET /api/products/{id}`  | service에 id를 전달한다 |
| PD-002 | 성공 | Controller | Product 상세 응답 존재          | HTTP 200 JSON으로 모든 필드를 반환한다 |
| PD-003 | 실패 | Controller | `id <= 0`                 | `400 Bad Request` |
| PD-005 | 실패 | Controller | id가 숫자가 아님                | `400 Bad Request` |
| PD-006 | 실패 | Controller | Product 없음                | `404 Not Found` |
| PD-007 | 성공 | Service | Product와 child 존재         | 상세 응답을 조립한다 |
| PD-008 | 실패 | Service | Product 없음                | `ProductNotFoundException` |
| PD-009 | Edge | Service | child 없음                  | 빈 list를 반환한다 |
| PD-010 | Edge | Service | `details = null`          | 빈 map을 반환한다 |
| PD-011 | Edge | Service | `details` blank           | 빈 map을 반환한다 |
| PD-012 | 성공 | Service | `details` JSON object     | map으로 반환한다 |
| PD-013 | 성공 | Repository | Product 본문 조회             | id 기준 projection을 반환한다 |
| PD-014 | Edge | Repository | Product 없음                | 빈 Optional을 반환한다 |
| PD-015 | 성공 | Repository | feature index/null 섞임     | index ASC, nulls last, id ASC |
| PD-016 | 성공 | Repository | description index/null 섞임 | index ASC, nulls last, id ASC |
| PD-017 | 성공 | Repository | image 여러 개                | MAIN 우선, id ASC |

---

## 3. RED 테스트 작성 계획

테스트는 세 그룹으로 작성한다.

- Controller: `ProductControllerGetProductDetailTest`
- Service: `ProductReadServiceGetProductDetailTest`
- Repository: `ProductRepositoryFindProductDetailTest`

RED 테스트는 아직 존재하지 않는 다음 타입과 메서드를 직접 참조한다.

- `ProductReadService#getProductDetail(Long id)`
- `ProductDetailResponse`
- `ProductFeatureResponse`
- `ProductDescriptionResponse`
- `ProductCategoryResponse`
- `ProductBoughtTogetherResponse`
- `ProductVideoResponse`
- `ProductNotFoundException`
- `ProductDetailProjection`
- child detail projection interfaces
- repository detail query methods

컴파일 실패는 RED 단계에서 허용한다. 구현 단계에서 위 계약을 만족시키며 GREEN으로 전환한다.
