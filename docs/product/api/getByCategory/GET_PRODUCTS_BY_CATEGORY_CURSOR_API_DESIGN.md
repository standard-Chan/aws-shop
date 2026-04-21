# Category별 Product Cursor 조회 API 설계 문서

**작성일**: 2026-04-21  
**대상**: AWS Shop 카테고리별 상품 목록 조회 API  
**범위**: `mainCategory`로 상품을 필터링하고, `averageRating` 또는 `ratingNumber` 기준 cursor pagination으로 상품 목록을 조회하는 API 설계

---

## 1. 목적

기존 `GET /api/products`는 `Product.id ASC` 기준 전체 상품 cursor 조회 API다.

이번 API는 특정 `mainCategory` 안에서 상품을 조회한다. 카테고리별 목록 화면에서는 단순 id 순서보다 평점 또는 리뷰 수 기준 정렬이 필요하므로, 정렬 기준을 선택할 수 있게 한다.

목표:

1. `mainCategory`로 상품을 필터링한다.
2. 기본 20개씩 조회한다.
3. cursor pagination을 사용한다.
4. 정렬 기준은 `averageRating` 또는 `ratingNumber` 중 하나를 선택한다.
5. 둘 다 요청되면 `averageRating`을 우선 적용한다.
6. 같은 정렬값을 가진 상품이 있을 수 있으므로 `id`를 tie-breaker로 사용한다.
7. 정렬 cursor가 안정적으로 동작하도록 응답 cursor에 `id`와 정렬 기준값을 함께 포함한다.
8. Product 목록과 대표 image를 native query 1번으로 함께 조회한다.

---

## 2. API 계약

### 2.1 Endpoint

```http
GET /api/products/category?mainCategory=HANDMADE_PRODUCTS&size=20&averageRating=true&cursorId=9000000000000&cursorAverageRating=4.5
```

### 2.2 Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `mainCategory` | string | true | 없음 | 조회할 상품 카테고리. `MainCategory` enum name 또는 하이픈 연결 표시값 |
| `size` | integer | false | `20` | 한 번에 가져올 상품 수 |
| `cursorId` | long | false | 없음 | 마지막으로 응답한 Product id |
| `cursorAverageRating` | decimal | false | 없음 | `averageRating` 정렬에서 마지막으로 응답한 rating 값 |
| `cursorRatingNumber` | integer | false | 없음 | `ratingNumber` 정렬에서 마지막으로 응답한 ratingNumber 값 |
| `averageRating` | boolean | false | `false` | `averageRating` 정렬 사용 여부 |
| `ratingNumber` | boolean | false | `false` | `ratingNumber` 정렬 사용 여부 |

요청자가 말한 "averageRating 혹은 ratingNumber 둘 중 하나 선택"은 boolean query parameter로 해석한다.

예시:

```http
GET /api/products/category?mainCategory=HANDMADE_PRODUCTS&averageRating=true
GET /api/products/category?mainCategory=HANDMADE_PRODUCTS&ratingNumber=true
```

둘 다 요청되면 `averageRating=true`가 우선이다.

```http
GET /api/products/category?mainCategory=HANDMADE_PRODUCTS&averageRating=true&ratingNumber=true
```

위 요청은 `averageRating` 정렬로 처리한다.

---

## 3. Parameter 정책

### 3.1 mainCategory

- 필수값이다.
- `MainCategory` enum name을 받는다.
- 띄어쓰기가 필요한 표시명은 공백 대신 `-`로 연결된 값도 허용한다.
- 예: `GIFT_CARDS`, `HANDMADE_PRODUCTS`, `SUBSCRIPTION_BOXES`
- 예: `Gift-Cards`, `Subscription-Boxes`
- 알 수 없는 값이면 `400 Bad Request`를 반환한다.

표시명 기반 값을 직접 받을 때는 URL query parameter 안정성을 위해 띄어쓰기 대신 `-`를 사용한다.

### 3.2 size

- 값이 없으면 `20`을 사용한다.
- 최소값은 `1`이다.
- 최대값은 `100`이다.
- 범위를 벗어나면 `400 Bad Request`를 반환한다.
- controller에서 Bean Validation `@Min(1)`, `@Max(100)`으로 검증한다.

### 3.3 sort 선택

정렬 선택 우선순위:

1. `averageRating=true`면 `averageRating` 정렬
2. `averageRating=false`이고 `ratingNumber=true`면 `ratingNumber` 정렬
3. 둘 다 없거나 false면 기본값으로 `averageRating` 정렬

정렬 방향:

- `averageRating DESC`
- `ratingNumber DESC`
- tie-breaker는 항상 `id ASC`

tie-breaker가 필요한 이유:

- 같은 평점 또는 같은 리뷰 수를 가진 상품이 많을 수 있다.
- cursor pagination에서 같은 정렬값을 안정적으로 이어가려면 `id`가 필요하다.

### 3.4 cursor

정렬 기준별로 cursor 값이 다르다.

`averageRating` 정렬 cursor:

- `cursorId`
- `cursorAverageRating`

`ratingNumber` 정렬 cursor:

- `cursorId`
- `cursorRatingNumber`

첫 페이지:

- cursor 관련 값이 없다.

다음 페이지:

- 이전 응답의 `nextCursor.id`와 정렬 기준 cursor 값을 함께 넘긴다.

cursor 검증:

- `cursorId`가 존재하지 않으면 `400 Bad Request`를 반환한다.
- `cursorId`가 존재하지만 요청한 `mainCategory`와 다른 상품이면 `400 Bad Request`를 반환한다.
- 정렬 기준에 필요한 cursor 값이 일부만 누락되면 `400 Bad Request`를 반환한다.

---

## 4. cursor에 정렬 기준값을 포함하는 이유

정렬 cursor pagination에서 원칙적으로 cursor는 다음 값을 포함한다.

- 마지막 상품의 정렬 기준값
- 마지막 상품의 id

예를 들어 `averageRating DESC, id ASC` 정렬이면 다음 페이지 조건은 아래와 같다.

```sql
p.average_rating < :cursorAverageRating
OR (p.average_rating = :cursorAverageRating AND p.id > :cursorId)
```

`nextCursor`에 id 외의 값을 넣는 이유는 다음과 같다.

- 다음 페이지 조회 시 cursor 상품을 다시 조회하지 않아도 된다.
- SQL 조건이 단순해진다.
- 정렬 기준값과 id를 함께 사용하므로 안정적인 cursor pagination이 가능하다.

결정:

- 응답에는 `nextCursor.id`와 현재 정렬 기준에 필요한 값을 반환한다.
- `averageRating` 정렬이면 `nextCursor.averageRating`을 함께 반환한다.
- `ratingNumber` 정렬이면 `nextCursor.ratingNumber`를 함께 반환한다.
- 정렬 기준에 필요 없는 cursor 필드는 null로 반환한다.

---

## 5. Response DTO

상품 요약 응답 구조는 기존 전체 상품 조회 DTO를 재사용한다.

### 5.1 최상위 응답

```java
public record ProductCategoryCursorResponse(
        List<ProductSummaryResponse> products,
        CategoryCursor nextCursor,
        boolean hasNext
) {
}
```

### 5.2 Cursor 응답

```java
public record CategoryCursor(
        Long id,
        BigDecimal averageRating,
        Integer ratingNumber
) {
}
```

필드 의미:

- `id`: 응답에 포함된 마지막 Product id
- `averageRating`: `averageRating` 정렬 시 마지막 Product의 averageRating
- `ratingNumber`: `ratingNumber` 정렬 시 마지막 Product의 ratingNumber

정렬 기준에 필요 없는 cursor 필드는 null로 반환한다.

### 5.3 상품 요약 응답

기존 `ProductSummaryResponse`를 재사용한다.

```java
public record ProductSummaryResponse(
        Long id,
        String parentAsin,
        String title,
        MainCategory mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store,
        ProductImageResponse image
) {
}
```

---

## 6. JSON 응답 예시

### 6.1 averageRating 정렬

```json
{
  "products": [
    {
      "id": 9000000000000,
      "parentAsin": "B07NTK7T5P",
      "title": "Daisy Keychain Wristlet Gray Fabric Key fob Lanyard",
      "mainCategory": "HANDMADE_PRODUCTS",
      "averageRating": 4.5,
      "ratingNumber": 12,
      "price": null,
      "store": "Generic",
      "image": {
        "variant": "MAIN",
        "thumb": "daisy-main-thumb",
        "large": "daisy-main-large",
        "hiRes": null
      }
    }
  ],
  "nextCursor": {
    "id": 9000000000000,
    "averageRating": 4.5,
    "ratingNumber": null
  },
  "hasNext": true
}
```

### 6.2 빈 결과

```json
{
  "products": [],
  "nextCursor": null,
  "hasNext": false
}
```

---

## 7. Pagination 처리 방식

정확한 `hasNext` 판단을 위해 DB에는 `size + 1`개를 요청한다.

처리 흐름:

1. `ProductController`가 `mainCategory`, `size`, 정렬 선택값, cursor 값을 받는다.
2. controller는 `size`를 Bean Validation으로 검증한다.
3. `ProductReadService`는 정렬 기준을 결정한다.
4. service는 조회 limit을 `size + 1`로 만든다.
5. repository는 `main_category = :mainCategory` 조건으로 필터링한다.
6. repository는 정렬 기준별 cursor 조건을 적용한다.
7. repository는 대표 image를 native query에서 함께 조회한다.
8. 조회 결과가 `size + 1`개이면 `hasNext = true`다.
9. 응답에는 앞의 `size`개만 담는다.
10. 응답 마지막 상품을 기준으로 `nextCursor`를 만든다.

---

## 8. Cursor 조건

### 8.1 averageRating 정렬

정렬:

```sql
ORDER BY p.average_rating DESC, p.id ASC
```

첫 페이지 조건:

```sql
WHERE p.main_category = :mainCategory
  AND p.average_rating IS NOT NULL
```

다음 페이지 조건:

```sql
WHERE p.main_category = :mainCategory
  AND p.average_rating IS NOT NULL
  AND (
      p.average_rating < :cursorAverageRating
      OR (p.average_rating = :cursorAverageRating AND p.id > :cursorId)
  )
```

설명:

- 높은 평점부터 조회하므로 다음 페이지는 `cursorAverageRating`보다 낮은 상품이다.
- 같은 평점이면 이전에 본 id보다 큰 id를 조회한다.

### 8.2 ratingNumber 정렬

정렬:

```sql
ORDER BY p.rating_number DESC, p.id ASC
```

첫 페이지 조건:

```sql
WHERE p.main_category = :mainCategory
  AND p.rating_number IS NOT NULL
```

다음 페이지 조건:

```sql
WHERE p.main_category = :mainCategory
  AND p.rating_number IS NOT NULL
  AND (
      p.rating_number < :cursorRatingNumber
      OR (p.rating_number = :cursorRatingNumber AND p.id > :cursorId)
  )
```

설명:

- 리뷰 수가 많은 상품부터 조회하므로 다음 페이지는 `cursorRatingNumber`보다 낮은 상품이다.
- 같은 리뷰 수이면 이전에 본 id보다 큰 id를 조회한다.

### 8.3 null 정렬값 정책

정렬 기준 값이 null인 상품이 존재할 수 있다.

정책:

- `averageRating` 정렬에서는 `average_rating IS NOT NULL` 상품만 조회한다.
- `ratingNumber` 정렬에서는 `rating_number IS NOT NULL` 상품만 조회한다.

이유:

- null 정렬값은 cursor 비교가 애매하다.
- 목록 정렬 품질도 낮아진다.

---

## 9. 대표 Image 조회 전략

기존 전체 상품 조회 API와 동일하다.

정책:

1. Product 목록과 대표 image를 native query 1번으로 함께 조회한다.
2. `product_images`에 `ROW_NUMBER()`를 적용한다.
3. `variant = 'MAIN'` 이미지를 우선한다.
4. `MAIN` 이미지가 없으면 `ProductImage.id ASC` 기준 첫 번째 이미지를 사용한다.
5. 이미지가 없으면 `image = null`을 반환한다.

---

## 10. Native Query 설계

아래 SQL은 응답 cursor에 포함된 정렬 기준값과 id를 사용해 다음 페이지를 조회한다.  
`cursorId`의 존재 여부와 `mainCategory` 일치 여부는 service에서 repository 검증 메서드로 먼저 확인한다.

### 10.1 averageRating 정렬 query

```sql
WITH ranked_images AS (
    SELECT
        pi.product_id,
        pi.variant,
        pi.thumb,
        pi.large,
        pi.hi_res,
        ROW_NUMBER() OVER (
            PARTITION BY pi.product_id
            ORDER BY
                CASE WHEN pi.variant = 'MAIN' THEN 0 ELSE 1 END,
                pi.id ASC
        ) AS rn
    FROM product_images pi
)
SELECT
    p.id,
    p.parent_asin,
    p.title,
    p.main_category,
    p.average_rating,
    p.rating_number,
    p.price,
    p.store,
    ri.variant AS image_variant,
    ri.thumb AS image_thumb,
    ri.large AS image_large,
    ri.hi_res AS image_hi_res
FROM (
    SELECT
        p.id,
        p.parent_asin,
        p.title,
        p.main_category,
        p.average_rating,
        p.rating_number,
        p.price,
        p.store
    FROM product p
    WHERE p.main_category = :mainCategory
      AND p.average_rating IS NOT NULL
      AND (
          :cursorId IS NULL
          OR p.average_rating < :cursorAverageRating
          OR (p.average_rating = :cursorAverageRating AND p.id > :cursorId)
      )
    ORDER BY p.average_rating DESC, p.id ASC
    LIMIT :limit
) p
LEFT JOIN ranked_images ri ON ri.product_id = p.id AND ri.rn = 1
ORDER BY p.average_rating DESC, p.id ASC
```

### 10.2 ratingNumber 정렬 query

```sql
WITH ranked_images AS (
    SELECT
        pi.product_id,
        pi.variant,
        pi.thumb,
        pi.large,
        pi.hi_res,
        ROW_NUMBER() OVER (
            PARTITION BY pi.product_id
            ORDER BY
                CASE WHEN pi.variant = 'MAIN' THEN 0 ELSE 1 END,
                pi.id ASC
        ) AS rn
    FROM product_images pi
)
SELECT
    p.id,
    p.parent_asin,
    p.title,
    p.main_category,
    p.average_rating,
    p.rating_number,
    p.price,
    p.store,
    ri.variant AS image_variant,
    ri.thumb AS image_thumb,
    ri.large AS image_large,
    ri.hi_res AS image_hi_res
FROM (
    SELECT
        p.id,
        p.parent_asin,
        p.title,
        p.main_category,
        p.average_rating,
        p.rating_number,
        p.price,
        p.store
    FROM product p
    WHERE p.main_category = :mainCategory
      AND p.rating_number IS NOT NULL
      AND (
          :cursorId IS NULL
          OR p.rating_number < :cursorRatingNumber
          OR (p.rating_number = :cursorRatingNumber AND p.id > :cursorId)
      )
    ORDER BY p.rating_number DESC, p.id ASC
    LIMIT :limit
) p
LEFT JOIN ranked_images ri ON ri.product_id = p.id AND ri.rn = 1
ORDER BY p.rating_number DESC, p.id ASC
```

---

## 11. 패키지 구조

새 controller와 새 service는 만들지 않는다. 기존 전체 조회와 동일한 흐름을 확장한다.

```text
jeong.awsshop.product.controller
└── ProductController

jeong.awsshop.product.service.productread
├── ProductReadService
└── ProductCategorySortType

jeong.awsshop.product.service.productread.dto
├── ProductCategoryCursorResponse
├── CategoryCursor
├── ProductSummaryResponse
└── ProductImageResponse

jeong.awsshop.product.repository
└── ProductRepository

jeong.awsshop.product.repository.projection
└── ProductSummaryNativeProjection
```

기존 `ProductSummaryResponse`, `ProductImageResponse`, `ProductSummaryNativeProjection`은 재사용한다.

---

## 12. Controller 설계

기존 `ProductController`에 메서드를 추가한다.

```java
@GetMapping("/category")
public ProductCategoryCursorResponse getProductsByCategory(
        @RequestParam String mainCategory,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) Long cursorId,
        @RequestParam(required = false) BigDecimal cursorAverageRating,
        @RequestParam(required = false) Integer cursorRatingNumber,
        @RequestParam(defaultValue = "false") boolean averageRating,
        @RequestParam(defaultValue = "false") boolean ratingNumber
) {
    return productReadService.getProductsByCategory(
            mainCategory,
            size,
            cursorId,
            cursorAverageRating,
            cursorRatingNumber,
            averageRating,
            ratingNumber
    );
}
```

controller 책임:

- query parameter 수신
- `size` Bean Validation
- `mainCategory` 문자열 전달
- 기존 `ProductReadService` 호출

---

## 13. Service 설계

기존 `ProductReadService`에 category 조회 메서드를 추가한다.

service 책임:

- 정렬 기준 결정
- `mainCategory` 문자열을 `MainCategory`로 변환
- cursor id 존재 여부와 category 일치 여부 검증
- cursor 값 조합 검증
- `size + 1` limit 계산
- repository 호출
- `ProductCategoryCursorResponse.from(rows, size, sortType)` 팩토리로 response DTO 조립

정렬 기준 결정:

```java
if (averageRating) {
    sortType = AVERAGE_RATING;
} else if (ratingNumber) {
    sortType = RATING_NUMBER;
} else {
    sortType = AVERAGE_RATING;
}
```

cursor 검증:

- `AVERAGE_RATING` 정렬에서 다음 페이지 요청이면 `cursorId`, `cursorAverageRating`이 함께 있어야 한다.
- `RATING_NUMBER` 정렬에서 다음 페이지 요청이면 `cursorId`, `cursorRatingNumber`가 함께 있어야 한다.
- cursor 값이 일부만 있으면 `400 Bad Request`를 반환한다.
- `cursorId`가 존재하지 않으면 `400 Bad Request`를 반환한다.
- `cursorId`가 존재하지만 요청한 `mainCategory`와 다른 상품이면 `400 Bad Request`를 반환한다.

---

## 14. Repository 설계

repository 메서드는 정렬 기준별로 분리한다.

```java
List<ProductSummaryNativeProjection> findCategoryProductSummariesOrderByAverageRating(
        MainCategory mainCategory,
        Long cursorId,
        BigDecimal cursorAverageRating,
        int limit
);

List<ProductSummaryNativeProjection> findCategoryProductSummariesOrderByRatingNumber(
        MainCategory mainCategory,
        Long cursorId,
        Integer cursorRatingNumber,
        int limit
);

boolean existsById(Long id);

boolean existsByIdAndMainCategory(Long id, MainCategory mainCategory);
```

분리 이유:

- 정렬 기준별 cursor 조건과 ORDER BY가 다르다.
- cursor id 존재 여부와 category 일치 여부를 명확히 검증해야 한다.
- 하나의 SQL에서 동적 정렬을 처리하면 조건이 복잡해진다.
- 테스트가 명확해진다.

---

## 15. 테스트 기준

### 15.1 Controller 테스트

- `mainCategory`, `size`, cursor 값, 정렬 parameter를 기존 `ProductReadService`에 전달한다.
- `size` 기본값은 `20`이다.
- `averageRating`, `ratingNumber`가 모두 없으면 기본 정렬은 service에서 averageRating으로 결정된다.
- `averageRating`, `ratingNumber`가 모두 true이면 service에서 averageRating이 우선된다.
- invalid `size`는 400이다.

### 15.2 Service 테스트

- averageRating 정렬 요청이면 averageRating repository 메서드를 호출한다.
- ratingNumber 정렬 요청이면 ratingNumber repository 메서드를 호출한다.
- 둘 다 true이면 averageRating repository 메서드를 호출한다.
- 둘 다 false이면 averageRating repository 메서드를 호출한다.
- `mainCategory`는 enum name과 하이픈 연결 표시값을 `MainCategory`로 변환한다.
- invalid `mainCategory`는 400이다.
- cursor 일부 누락은 400이다.
- cursor id가 존재하지 않으면 400이다.
- cursor id가 요청한 category와 다르면 400이다.
- `size + 1`로 repository limit을 전달한다.
- `hasNext`, `nextCursor`를 계산한다.
- averageRating 정렬 응답의 `nextCursor`에는 `id`, `averageRating`만 포함하고 `ratingNumber`는 null이다.
- ratingNumber 정렬 응답의 `nextCursor`에는 `id`, `ratingNumber`만 포함하고 `averageRating`은 null이다.

### 15.3 Repository 테스트

- `main_category`가 일치하는 상품만 조회한다.
- averageRating 정렬은 `average_rating DESC, id ASC`다.
- ratingNumber 정렬은 `rating_number DESC, id ASC`다.
- averageRating cursor 조건이 `cursorAverageRating`, `cursorId`를 기준으로 다음 페이지를 정확히 조회한다.
- ratingNumber cursor 조건이 `cursorRatingNumber`, `cursorId`를 기준으로 다음 페이지를 정확히 조회한다.
- cursor id 존재 여부를 조회할 수 있다.
- cursor id와 category 일치 여부를 조회할 수 있다.
- 대표 image는 기존 정책대로 1장만 조회한다.

---

## 16. 결정 사항

- 기본 page size는 `20`이다.
- 최대 page size는 `100`이다.
- category query parameter는 `MainCategory` enum name과 하이픈 연결 표시값을 허용한다.
- 정렬 선택은 boolean query parameter `averageRating`, `ratingNumber`로 받는다.
- 둘 다 true이면 `averageRating`을 우선한다.
- 둘 다 false이거나 없으면 `averageRating`을 기본 정렬로 사용한다.
- 정렬 방향은 DESC다.
- tie-breaker는 `id ASC`다.
- cursor는 정렬 기준값과 id를 함께 사용한다.
- 응답은 `ProductCategoryCursorResponse`를 사용하고 `nextCursor`에 id와 정렬 기준값을 함께 반환한다.
- cursor id가 존재하지 않으면 400을 반환한다.
- cursor id가 요청한 `mainCategory`와 다른 상품이면 400을 반환한다.
- null 정렬값은 조회 대상에서 제외한다.
- 대표 image 조회는 기존 getAll API와 동일하게 native query의 `ROW_NUMBER()` 방식으로 처리한다.
