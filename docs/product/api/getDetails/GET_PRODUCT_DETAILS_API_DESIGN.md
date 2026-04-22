# Product 상세 조회 API 설계 문서

## 요약

`GET /api/products/{id}`는 Product id로 단일 상품 상세 페이지에 필요한 모든 상품 정보를 조회하는 API다. 응답에는 `Product` 본문 필드와 모든 하위 정보인 `features`, `descriptions`, `categories`, `boughtTogether`, `images`, `videos`, `details`를 빠짐없이 포함한다. 단, child table의 내부 DB id는 클라이언트가 상세 페이지를 그리는 데 필요한 상품 정보가 아니므로 응답하지 않는다.

구현은 TDD로 진행한다. RED 단계에서는 Controller, Service, Repository 테스트를 먼저 작성하고, 단일 상품이 없을 때 `404 Not Found`, id 형식이 잘못됐을 때 `400 Bad Request`, 상품이 있을 때 전체 상세 응답을 반환하는 계약을 고정한다. 조회 전략은 Product 1건 조회 특성상 pagination 문제는 없지만, 여러 1:N 컬렉션을 한 번에 fetch join하면 Cartesian product가 커지므로 Product 본문과 child collection을 projection으로 분리 조회한 뒤 Service 또는 DTO factory에서 조립하는 방향을 기본안으로 둔다.

---

## 1. 목적

실제 이커머스 상품 상세 페이지는 목록 카드보다 훨씬 많은 정보를 필요로 한다.

이 API의 목표는 다음과 같다.

1. Product id로 단일 상품을 조회한다.
2. Product 엔티티에 저장된 모든 본문 필드를 반환한다.
3. Product에 연결된 모든 child entity 정보를 반환한다.
4. 상세 페이지에서 사용할 수 있도록 이미지, 영상, 특징, 설명, 카테고리, 함께 구매한 상품 정보를 누락 없이 제공한다.
5. 없는 상품은 명확하게 `404 Not Found`로 응답한다.
6. TDD 흐름에서 API 계약, 실패 케이스, 조회 조립 방식을 테스트로 먼저 고정한다.

---

## 2. API 계약

### 2.1 Endpoint

```http
GET /api/products/{id}
```

예시:

```http
GET /api/products/9000000000000
```

기존 `GET /api/products/category`와 같은 prefix를 공유한다. Spring MVC는 더 구체적인 static path를 우선 매칭하므로 `/api/products/category`는 category 목록 조회 API로 유지하고, `/api/products/{id}`는 단일 상세 조회에 사용한다.

### 2.2 Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `id` | long | true | 조회할 Product id |

### 2.3 Parameter 정책

`id` 정책:

- `id`는 `Product.id`를 의미한다.
- `id`는 양수만 허용한다.
- `id <= 0`이면 `400 Bad Request`를 반환한다.
- 숫자로 변환할 수 없는 값이면 Spring MVC path variable binding 실패로 `400 Bad Request`를 반환한다.
- id에 해당하는 Product가 없으면 `404 Not Found`를 반환한다.

---

## 3. 응답 범위

요청자가 말한 "Product에 들어있는 모든 정보"는 현재 도메인 기준으로 아래 전체를 의미한다.

Product 본문:

- `id`
- `parentAsin`
- `title`
- `mainCategory`
- `averageRating`
- `ratingNumber`
- `price`
- `store`
- `details`

Product child collections:

- `features`
- `descriptions`
- `categories`
- `boughtTogether`
- `images`
- `videos`

목록 API의 `ProductSummaryResponse`와 다르게 상세 API는 대표 이미지 1장만 반환하지 않는다. `product_images`에 저장된 모든 이미지를 반환한다.

응답하지 않는 값:

- `ProductFeature.id`
- `ProductDescription.id`
- `ProductCategory.id`
- `ProductBoughtTogether.id`
- `ProductImage.id`
- `ProductVideo.id`
- 각 child row의 `product_id`

이 값들은 DB 내부 식별자 또는 FK이며, 상세 페이지에서 사용자에게 보여줄 상품 정보가 아니다. 정렬 tie-breaker로는 사용할 수 있지만 API 응답 계약에는 포함하지 않는다.

---

## 4. Response DTO

### 4.1 최상위 응답

```java
public record ProductDetailResponse(
        Long id,
        String parentAsin,
        String title,
        MainCategory mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store,
        Map<String, Object> details,
        List<ProductFeatureResponse> features,
        List<ProductDescriptionResponse> descriptions,
        List<ProductCategoryResponse> categories,
        List<ProductBoughtTogetherResponse> boughtTogether,
        List<ProductImageResponse> images,
        List<ProductVideoResponse> videos
) {
}
```

`details`는 DB에 JSON 문자열로 저장되어 있으므로 응답에서는 JSON object로 반환한다. DB 값이 `null`이거나 빈 JSON이면 `{}`를 반환한다.

응답 collection은 항상 non-null이다. 데이터가 없으면 `[]`를 반환한다.

### 4.2 Feature 응답

```java
public record ProductFeatureResponse(
        Integer featureIndex,
        String feature
) {
}
```

정렬:

- `featureIndex ASC`
- `featureIndex`가 null이면 뒤로 보낸다.
- 같은 index 또는 index가 없으면 DB `id ASC`를 보조 정렬로 사용한다.

### 4.3 Description 응답

```java
public record ProductDescriptionResponse(
        Integer descriptionIndex,
        String description
) {
}
```

정렬:

- `descriptionIndex ASC`
- `descriptionIndex`가 null이면 뒤로 보낸다.
- 같은 index 또는 index가 없으면 DB `id ASC`를 보조 정렬로 사용한다.

### 4.4 Category 응답

```java
public record ProductCategoryResponse(
        String category
) {
}
```

정렬:

- DB `id ASC`
- 원본 JSON 배열 순서를 정확히 복원할 index 컬럼이 현재 없으므로 저장 순서에 가까운 `id ASC`를 사용한다.

### 4.5 Bought Together 응답

```java
public record ProductBoughtTogetherResponse(
        Long relatedProductId,
        String relatedProductTitle,
        String relatedProductImageUrl
) {
}
```

정렬:

- DB `id ASC`

### 4.6 Image 응답

기존 목록 API의 `ProductImageResponse` 구조를 재사용할 수 있다. 단, 상세 API에서는 단수 `image`가 아니라 모든 이미지를 담는 `images` 배열로 반환한다.

```java
public record ProductImageResponse(
        String variant,
        String thumb,
        String large,
        String hiRes
) {
}
```

정렬:

- `variant = 'MAIN'` 이미지를 먼저 반환한다.
- 이후 DB `id ASC`

상세 페이지에서 첫 이미지를 대표 이미지처럼 사용할 수 있도록 `MAIN`을 맨 앞으로 둔다.

### 4.7 Video 응답

```java
public record ProductVideoResponse(
        String title,
        String url,
        String userId
) {
}
```

정렬:

- DB `id ASC`

---

## 5. JSON 응답 예시

```json
{
  "id": 9000000000000,
  "parentAsin": "B07NK78DVV",
  "title": "Psychedelic Swirls Key Fob",
  "mainCategory": "HANDMADE_PRODUCTS",
  "averageRating": 4.9,
  "ratingNumber": 14,
  "price": 17.99,
  "store": "Green Acorn Kitchen",
  "details": {
    "Package Dimensions": "8.46 x 3.86 x 0.31 inches; 0.78 Ounces",
    "Department": "unisex-adult",
    "Date First Available": "April 1, 2017"
  },
  "features": [
    {
      "featureIndex": 0,
      "feature": "6\" x 1\" loop with swivel clip"
    }
  ],
  "descriptions": [
    {
      "descriptionIndex": 0,
      "description": "A colorful way to carry your keys..."
    }
  ],
  "categories": [
    {
      "category": "Handmade Products"
    },
    {
      "category": "Clothing, Shoes & Accessories"
    }
  ],
  "boughtTogether": [],
  "images": [
    {
      "variant": "MAIN",
      "thumb": "https://m.media-amazon.com/images/I/51R+ZyYT8ML._SS40_.jpg",
      "large": "https://m.media-amazon.com/images/I/51R+ZyYT8ML.jpg",
      "hiRes": "https://m.media-amazon.com/images/I/91E0YxyKpLL._SL1500_.jpg"
    }
  ],
  "videos": []
}
```

---

## 6. 오류 응답

### 6.1 존재하지 않는 Product

```http
HTTP/1.1 404 Not Found
```

응답 body는 현재 공통 예외 처리 방식과 맞춰 별도 표준 body를 강제하지 않는다. RED 테스트에서는 우선 HTTP status가 `404 Not Found`인지 검증한다.

구현 방향:

- `ProductNotFoundException`은 `ResponseStatusException`을 상속한다.
- status는 `HttpStatus.NOT_FOUND`로 고정한다.
- reason은 `"[Product 조회 실패]: 존재하지 않는 상품입니다."`처럼 product read 계열 메시지로 둔다.
- 별도 오류 응답 body 표준화는 이번 API 구현 범위에 포함하지 않는다.

### 6.2 유효하지 않은 id

```http
HTTP/1.1 400 Bad Request
```

대상:

- `id <= 0`
- 숫자로 변환할 수 없는 path variable

구현 후보:

- Controller path variable에 `@Positive Long id` 적용
- `ProductController`에 이미 붙은 `@Validated`를 재사용
- 숫자로 변환할 수 없는 path variable은 Spring MVC의 기본 type mismatch 처리를 사용한다.

RED 테스트에서는 status code를 검증한다. 오류 body 형식은 검증 대상에서 제외한다.

---

## 7. 조회 전략

### 7.1 기본안: Product 본문 1번 + child collection projection 분리 조회

단일 상세 조회이므로 목록 API처럼 cursor pagination은 없다. 그러나 Product에 1:N 컬렉션이 여러 개 있다.

여러 컬렉션을 한 쿼리에서 fetch join하면 다음 문제가 생긴다.

- `features x descriptions x categories x images x videos` 형태의 row 증폭이 발생한다.
- 중복 제거를 위해 JPA persistence context와 컬렉션 조립 비용이 커진다.
- 데이터가 많은 상품에서 불필요하게 큰 result set이 만들어진다.

따라서 기본 조회 흐름은 다음으로 둔다.

1. `product` 테이블에서 Product 본문 projection을 id로 조회한다.
2. Product가 없으면 `ProductNotFoundException`을 던진다.
3. 각 child repository가 `product_id` 기준으로 child projection collection을 조회한다.
4. Service 또는 `ProductDetailResponse.from(...)` factory가 응답 DTO를 조립한다.

현재 domain entity에는 public getter가 없다. 따라서 상세 응답 조립은 entity를 직접 반환받아 getter로 읽는 방식보다 read projection을 사용하는 방식이 현재 코드 스타일과 잘 맞는다. 목록 API도 native projection을 사용하고 있으므로 상세 API도 repository projection 패키지 아래에 projection interface를 둔다.

예상 projection:

```java
public interface ProductDetailProjection {
    Long getId();
    String getParentAsin();
    String getTitle();
    String getMainCategory();
    BigDecimal getAverageRating();
    Integer getRatingNumber();
    BigDecimal getPrice();
    String getStore();
    String getDetails();
}
```

child projection은 응답 DTO에 필요한 비즈니스 필드만 포함한다.

예상 repository method는 모두 `@Query` 기반으로 작성한다. 아래 이름은 의도를 드러내기 위한 후보이며, Spring Data derived query로 해석되게 두지 않는다.

```java
@Query("""
        SELECT
            p.id AS id,
            p.parentAsin AS parentAsin,
            p.title AS title,
            p.mainCategory AS mainCategory,
            p.averageRating AS averageRating,
            p.ratingNumber AS ratingNumber,
            p.price AS price,
            p.store AS store,
            p.details AS details
        FROM Product p
        WHERE p.id = :id
        """)
Optional<ProductDetailProjection> findDetailById(@Param("id") Long id);

@Query(...)
List<ProductFeatureDetailProjection> findFeatureDetailsByProductId(Long productId);

@Query(...)
List<ProductDescriptionDetailProjection> findDescriptionDetailsByProductId(Long productId);

@Query(...)
List<ProductCategoryDetailProjection> findCategoryDetailsByProductId(Long productId);

@Query(...)
List<ProductBoughtTogetherDetailProjection> findBoughtTogetherDetailsByProductId(Long productId);

@Query(...)
List<ProductImageDetailProjection> findImageDetailsByProductId(Long productId);

@Query(...)
List<ProductVideoDetailProjection> findVideoDetailsByProductId(Long productId);
```

현재 child repository들이 이미 분리되어 있으므로 그 경계를 활용한다.

### 7.2 정렬 쿼리 정책

Spring Data derived query의 `OrderByFeatureIndexAscIdAsc`만으로는 null을 뒤로 보내는 정렬을 DB별로 안정적으로 보장하기 어렵다. 따라서 index 기반 child 조회는 명시적인 `@Query`를 사용한다.

Feature 정렬:

```sql
ORDER BY
  CASE WHEN pf.feature_index IS NULL THEN 1 ELSE 0 END,
  pf.feature_index ASC,
  pf.id ASC
```

Description 정렬:

```sql
ORDER BY
  CASE WHEN pd.description_index IS NULL THEN 1 ELSE 0 END,
  pd.description_index ASC,
  pd.id ASC
```

Image 정렬:

```sql
ORDER BY
  CASE WHEN pi.variant = 'MAIN' THEN 0 ELSE 1 END,
  pi.id ASC
```

Category, boughtTogether, video 정렬:

```sql
ORDER BY id ASC
```

### 7.3 details JSON 처리

`Product.details`는 DB에 JSON 문자열로 저장된다. 상세 응답에서는 문자열이 아니라 JSON object로 반환해야 한다.

처리 정책:

- `details == null`이면 빈 map `{}`를 반환한다.
- `details`가 빈 문자열이면 빈 map `{}`를 반환한다.
- `details`가 `{}`이면 빈 map `{}`를 반환한다.
- 정상 JSON object이면 `Map<String, Object>`로 변환한다.
- JSON 파싱 실패는 저장 데이터 불일치이므로 `500 Internal Server Error`로 본다.

구현 후보:

- Jackson `ObjectMapper.readValue(details, new TypeReference<Map<String, Object>>() {})`
- 기존 `common/json` 유틸이 상세 응답 변환에 적합한지 확인 후 재사용

JSON value type은 문자열로 제한하지 않는다. 원본 `details`가 숫자, boolean, nested object, array 값을 가질 수 있으므로 `Map<String, Object>`로 유지한다.

### 7.4 트랜잭션과 일관성

상세 조회는 여러 repository query로 조립된다. Service method에는 `@Transactional(readOnly = true)`를 적용한다.

목표:

- 같은 요청 안에서 Product 본문과 child collection 조회를 하나의 read transaction으로 묶는다.
- DTO 조립 시점에 lazy loading이 발생하지 않게 projection 결과만 사용한다.
- Product 본문 조회 후 child 조회 사이에 데이터가 변경되는 문제는 현재 서비스에 상품 수정 API가 없으므로 이번 범위에서 별도 lock을 사용하지 않는다.

---

## 8. Service 설계

예상 public method:

```java
@Transactional(readOnly = true)
public ProductDetailResponse getProductDetail(Long id)
```

처리 흐름:

1. id가 정상이라는 전제는 Controller validation에 둔다.
2. `ProductRepository`에서 Product 본문을 조회한다.
3. 없으면 `ProductNotFoundException`을 던진다.
4. child repository들에서 하위 projection을 조회한다.
5. `ProductDetailResponse.from(...)`에 projection들을 전달한다.
6. DTO factory가 `details` JSON을 `Map<String, Object>`로 변환한다.
7. `ProductDetailResponse`를 반환한다.

Service 책임:

- 조회 orchestration
- not found 처리
- DTO factory 호출

DTO 책임:

- projection을 response field로 변환
- 빈 collection을 `[]`로 유지
- `details` null을 `{}`로 정규화
- image, feature, description 등 child DTO 생성

Repository 책임:

- Product 본문 projection 조회
- child projection 조회
- child collection 정렬 보장

---

## 9. TDD 계획

### 9.1 RED 테스트 목록

| ID | 구분 | 테스트 대상 | 조건 | 기대 결과 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| PD-001 | 성공 | Controller | `GET /api/products/{id}` | service에 id를 전달하고 상세 응답을 반환한다 | 높음 |
| PD-002 | 실패 | Controller | `id = 0` | `400 Bad Request` | 높음 |
| PD-003 | 실패 | Controller | `id < 0` | `400 Bad Request` | 높음 |
| PD-004 | 실패 | Controller | `id`가 숫자가 아님 | `400 Bad Request` | 중간 |
| PD-005 | 성공 | Service | Product가 존재 | Product 본문과 모든 child 정보를 조립해 반환한다 | 높음 |
| PD-006 | 실패 | Service | Product가 없음 | `ProductNotFoundException` | 높음 |
| PD-007 | 성공 | Service/DTO | child collection이 비어 있음 | 응답 collection은 `[]` | 높음 |
| PD-008 | 성공 | Service/DTO | `details = null` | `details = {}` | 중간 |
| PD-009 | 성공 | Service/DTO | `details` JSON object 존재 | `Map<String, Object>`로 반환한다 | 높음 |
| PD-010 | 성공 | Service/DTO | child row 내부 id 존재 | 응답에는 child 내부 id와 productId가 포함되지 않는다 | 중간 |
| PD-011 | 성공 | Repository | Product 본문 조회 | id에 해당하는 본문 projection을 조회한다 | 높음 |
| PD-012 | 성공 | Repository | Product 없음 | 빈 Optional을 반환한다 | 높음 |
| PD-013 | 성공 | Repository | feature/description 존재 | index null은 뒤로 보내고 index/id 기준으로 정렬한다 | 중간 |
| PD-014 | 성공 | Repository | image 여러 개 존재 | `MAIN` 이미지가 첫 번째로 조회된다 | 중간 |
| PD-015 | 성공 | Repository | boughtTogether 없음 | 빈 list를 반환한다 | 낮음 |
| PD-016 | 실패 | Service/DTO | details JSON 파싱 실패 | 서버 오류 계열 예외로 전파한다 | 낮음 |

### 9.2 Controller 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/controller/getproductdetail/ProductControllerGetProductDetailTest.java
```

테스트 메서드 후보:

```java
should_return_product_detail_when_product_exists
should_return_bad_request_when_id_is_zero
should_return_bad_request_when_id_is_negative
should_return_bad_request_when_id_is_not_number
should_return_not_found_when_product_does_not_exist
```

검증 범위:

- endpoint path가 `GET /api/products/{id}`인지 확인한다.
- `ProductReadService#getProductDetail(id)` 호출 여부를 확인한다.
- validation 실패가 `400`인지 확인한다.
- service의 not found 예외가 `404`로 매핑되는지 확인한다.

### 9.3 Service 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/service/productread/getproductdetail/ProductReadServiceGetProductDetailTest.java
```

테스트 메서드 후보:

```java
should_return_all_product_detail_fields
should_return_all_child_collections
should_throw_product_not_found_exception_when_product_does_not_exist
should_return_empty_collections_when_children_do_not_exist
should_return_empty_details_when_details_is_null
should_return_empty_details_when_details_is_blank
should_parse_details_json_to_map
should_not_include_child_internal_ids
```

검증 범위:

- Product 본문 필드가 모두 응답에 포함되는지 확인한다.
- `features`, `descriptions`, `categories`, `boughtTogether`, `images`, `videos`가 모두 포함되는지 확인한다.
- child가 없을 때 null이 아니라 빈 list인지 확인한다.
- `details`가 문자열이 아니라 JSON object 구조로 반환되는지 확인한다.
- child row 내부 id와 productId가 응답 DTO에 노출되지 않는지 확인한다.

### 9.4 Repository 테스트

파일 후보:

```text
src/test/java/jeong/awsshop/product/repository/findproductdetail/ProductRepositoryFindProductDetailTest.java
```

테스트 메서드 후보:

```java
should_find_product_body_by_id
should_return_empty_optional_when_product_does_not_exist
should_find_features_ordered_by_feature_index_with_nulls_last
should_find_descriptions_ordered_by_description_index_with_nulls_last
should_find_categories_ordered_by_id
should_find_bought_together_ordered_by_id
should_find_images_with_main_image_first
should_find_videos_ordered_by_id
```

검증 범위:

- DB에 저장된 실제 entity 또는 projection 조회 결과를 확인한다.
- JSONL fixture를 사용할 수 있으면 기존 data import 흐름과 같은 데이터를 사용한다.
- child repository별 정렬 정책을 고정한다.
- repository는 entity가 아니라 상세 조회용 projection을 반환한다.

---

## 10. 구현 대상 후보

새로 추가될 가능성이 높은 타입:

```text
src/main/java/jeong/awsshop/product/service/productread/dto/ProductDetailResponse.java
src/main/java/jeong/awsshop/product/service/productread/dto/ProductFeatureResponse.java
src/main/java/jeong/awsshop/product/service/productread/dto/ProductDescriptionResponse.java
src/main/java/jeong/awsshop/product/service/productread/dto/ProductCategoryResponse.java
src/main/java/jeong/awsshop/product/service/productread/dto/ProductBoughtTogetherResponse.java
src/main/java/jeong/awsshop/product/service/productread/dto/ProductVideoResponse.java
src/main/java/jeong/awsshop/product/exception/productread/ProductNotFoundException.java
src/main/java/jeong/awsshop/product/repository/projection/ProductDetailProjection.java
src/main/java/jeong/awsshop/product/repository/projection/ProductFeatureDetailProjection.java
src/main/java/jeong/awsshop/product/repository/projection/ProductDescriptionDetailProjection.java
src/main/java/jeong/awsshop/product/repository/projection/ProductCategoryDetailProjection.java
src/main/java/jeong/awsshop/product/repository/projection/ProductBoughtTogetherDetailProjection.java
src/main/java/jeong/awsshop/product/repository/projection/ProductImageDetailProjection.java
src/main/java/jeong/awsshop/product/repository/projection/ProductVideoDetailProjection.java
```

수정될 가능성이 높은 타입:

```text
src/main/java/jeong/awsshop/product/controller/ProductController.java
src/main/java/jeong/awsshop/product/service/productread/ProductReadService.java
src/main/java/jeong/awsshop/product/repository/ProductRepository.java
src/main/java/jeong/awsshop/product/repository/ProductFeatureRepository.java
src/main/java/jeong/awsshop/product/repository/ProductDescriptionRepository.java
src/main/java/jeong/awsshop/product/repository/ProductCategoryRepository.java
src/main/java/jeong/awsshop/product/repository/ProductBoughtTogetherRepository.java
src/main/java/jeong/awsshop/product/repository/ProductImageRepository.java
src/main/java/jeong/awsshop/product/repository/ProductVideoRepository.java
src/main/java/jeong/awsshop/common/exception/GlobalControllerExceptionHandler.java
```

---

## 11. 비범위

이번 상세 조회 API 설계에서 제외하는 항목:

- 리뷰 목록 조회
- 재고/배송/장바구니 정보
- 추천 상품 계산
- 같은 상품의 variant 옵션 그룹핑
- 캐시 적용
- 검색 색인 연동
- 상세 페이지 HTML 렌더링
- 인증/인가

현재 Product 도메인에 존재하지 않는 정보는 반환하지 않는다.

---

## 12. 결정 사항

- endpoint는 `GET /api/products/{id}`로 한다.
- 기존 `GET /api/products/category`는 그대로 유지한다.
- `id`는 Product id 기준이다. `parentAsin` 조회는 이번 범위에 포함하지 않는다.
- 상세 응답은 Product 본문과 모든 child collection을 포함한다.
- child table 내부 DB id와 `product_id`는 응답하지 않는다.
- `details`는 JSON 문자열이 아니라 JSON object로 반환한다.
- child collection이 없으면 null이 아니라 빈 배열을 반환한다.
- 이미지 상세 응답은 전체 이미지를 반환하며, `MAIN` 이미지를 첫 번째로 정렬한다.
- Product가 없으면 `404 Not Found`를 반환한다.
- 오류 응답 body 형식은 이번 범위에서 표준화하지 않고 status code 중심으로 테스트한다.
- repository 조회 결과는 entity가 아니라 상세 조회용 projection을 기본으로 한다.
- 구현은 하지 않고, 다음 단계에서 RED 테스트부터 작성한다.
