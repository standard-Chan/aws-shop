# Product 목록 Cursor 조회 API 설계 문서

**작성일**: 2026-04-21  
**대상**: AWS Shop 상품 목록 조회 API  
**범위**: `id` 기준 cursor pagination으로 Product 목록을 조회하고, 목록 데이터와 다음 cursor 값을 반환하는 API 설계

---

## 1. 목적

상품 데이터의 양이 많기 때문에 offset pagination 대신 cursor pagination을 사용한다.

이 API의 목표는 다음과 같다.

1. `Product.id` 기준으로 안정적인 순서를 보장한다.
2. 매 요청마다 `size` 개수만큼 상품을 조회한다.
3. 이전 응답의 `nextCursorId`를 다음 요청의 `cursor`로 사용한다.
4. 목록 화면에 필요한 필드만 조회한다.
5. 대량 데이터에서도 불필요한 row scan과 entity loading을 줄인다.

---

## 2. API 계약

### 2.1 Endpoint

```http
GET /api/products?size=20&cursor=1000
```

### 2.2 Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `size` | integer | false | `20` | 한 번에 가져올 상품 수 |
| `cursor` | long | false | 없음 | 마지막으로 조회한 Product `id` |

### 2.3 Parameter 정책

`size` 정책:

- 값이 없으면 `20`을 사용한다.
- 최소값은 `1`이다.
- 최대값은 `100`이다.
- 범위를 벗어나면 `400 Bad Request`를 반환한다.

`cursor` 정책:

- 값이 없으면 첫 페이지로 간주한다.
- 값이 있으면 `id > cursor` 조건으로 다음 페이지를 조회한다.
- 음수 또는 `0`은 유효하지 않은 cursor로 보고 `400 Bad Request`를 반환한다.
- `cursor`는 마지막으로 응답한 Product `id`를 의미한다.

정렬 정책:

- `Product.id ASC`
- cursor 조건은 `id > cursor`

`nextCursorId`는 응답에 포함된 마지막 Product id다. 따라서 다음 요청에서 같은 상품을 중복 조회하지 않으려면 `id > cursor` 조건을 사용한다.

예시:

- 첫 응답 product ids: `[10, 11, 12]`
- 첫 응답 `nextCursorId`: `12`
- 다음 요청: `GET /api/products?size=3&cursor=12`
- 다음 조회 조건: `id > 12`
- 다음 응답 product ids: `[13, 14, 15]`

`id >= cursor`를 사용하면 `12`가 다음 응답에 다시 포함되므로 사용하지 않는다.

---

## 3. 응답 필드

DB에서 가져올 Product 데이터는 다음으로 제한한다.

- `id`
- `parentAsin`
- `title`
- `mainCategory`
- `averageRating`
- `ratingNumber`
- `price`
- `store`
- `image`

요청 문구의 `averageRaating`은 도메인 필드명 기준으로 `averageRating`으로 정규화한다.

---

## 4. Response DTO

### 4.1 최상위 응답

```java
public record ProductCursorResponse(
        List<ProductSummaryResponse> products,
        Long nextCursorId,
        boolean hasNext
) {
}
```

필드 의미:

- `products`: 조회된 상품 목록
- `nextCursorId`: 다음 요청에 넘길 cursor 값. 응답에 포함된 마지막 Product id다.
- `hasNext`: 다음 페이지 존재 여부

### 4.2 상품 요약 응답

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

### 4.3 이미지 응답

```java
public record ProductImageResponse(
        String variant,
        String thumb,
        String large,
        String hiRes
) {
}
```

---

## 5. JSON 응답 예시

```json
{
  "products": [
    {
      "id": 1001,
      "parentAsin": "B07NTK7T5P",
      "title": "Daisy Keychain Wristlet Gray Fabric Key fob Lanyard",
      "mainCategory": "HANDMADE",
      "averageRating": 4.5,
      "ratingNumber": 12,
      "price": null,
      "store": "Generic",
      "image": {
        "variant": "MAIN",
        "thumb": "https://m.media-amazon.com/images/I/41J3kMGt34L._SS40_.jpg",
        "large": "https://m.media-amazon.com/images/I/41J3kMGt34L.jpg",
        "hiRes": null
      }
    }
  ],
  "nextCursorId": 1001,
  "hasNext": true
}
```

빈 결과 응답:

```json
{
  "products": [],
  "nextCursorId": null,
  "hasNext": false
}
```

---

## 6. Pagination 처리 방식

정확한 `hasNext` 판단을 위해 DB에는 `size + 1`개를 요청한다.

처리 흐름:

1. controller가 `size`, `cursor`를 받는다.
2. service가 조회 limit을 `size + 1`로 만든다.
3. repository는 `id > cursor` 조건과 `id ASC` 정렬로 조회한다.
4. 조회 결과가 `size + 1`개이면 `hasNext = true`다.
5. 응답에는 앞의 `size`개만 담는다.
6. 응답에 포함된 마지막 상품의 `id`를 `nextCursorId`로 반환한다.
7. 응답할 상품이 없으면 `nextCursorId = null`, `hasNext = false`를 반환한다.

첫 페이지:

```sql
SELECT ...
FROM product p
WHERE 1 = 1
ORDER BY p.id ASC
LIMIT :limit
```

다음 페이지:

```sql
SELECT ...
FROM product p
WHERE p.id > :cursor
ORDER BY p.id ASC
LIMIT :limit
```

---

## 7. 대표 Image 조회 전략

목록 API에서는 상품별 이미지 전체가 아니라 대표 이미지 1장만 반환한다.

`images`는 `Product`와 `ProductImage`의 1:N 관계다. fetch join으로 컬렉션을 가져오면 상품 1개가 image 개수만큼 row로 늘어나기 때문에 pagination 결과가 깨질 수 있다.

또한 JPA fetch join은 컬렉션에서 "첫 번째 image 1개만"을 portable하게 제한하는 용도에 적합하지 않다. `join fetch p.images`는 컬렉션 전체 로딩을 의미하며, `limit 1`을 상품별로 적용하는 기능이 아니다.

### 7.1 확정안: native query 단일 조회

Product 목록과 대표 이미지는 native query 1번으로 함께 조회한다.

처리 방식:

1. Product를 `id > cursor`, `id ASC`, `LIMIT size + 1` 기준으로 먼저 제한한다.
2. `product_images`에는 `ROW_NUMBER()`를 적용해 product별 이미지 우선순위를 계산한다.
3. `variant = 'MAIN'` 이미지에 가장 높은 우선순위를 부여한다.
4. `MAIN` 이미지가 없으면 `ProductImage.id ASC` 기준 첫 번째 이미지가 선택된다.
5. `rn = 1`인 대표 이미지만 Product 목록 결과에 `LEFT JOIN`한다.

장점:

- pagination이 안정적이다.
- Product 목록과 대표 이미지를 한 번에 가져온다.
- 이미지 N+1 문제를 피할 수 있다.
- DB에서 상품별 대표 image 1장만 반환한다.
- service는 native query 결과를 response DTO로 변환하기만 한다.

대표 이미지 기준:

1. `variant = 'MAIN'` 이미지가 있으면 우선 사용한다.
2. `MAIN` 이미지가 없으면 `ProductImage.id ASC` 기준 첫 번째 이미지를 사용한다.
3. 이미지가 없으면 `image = null`을 반환한다.

현재 `ProductImage`에는 JSON 배열의 원래 순서를 보존하는 `sortOrder` 컬럼이 없다. 따라서 "첫 번째 이미지"는 DB에 저장된 `ProductImage.id ASC` 기준으로 정의한다.

### 7.2 Native query SQL

MySQL 8 이상 기준으로 `ROW_NUMBER()`를 사용한다.

```sql
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
    WHERE (:cursor IS NULL OR p.id > :cursor)
    ORDER BY p.id ASC
    LIMIT :limit
) p
LEFT JOIN (
    SELECT
        ranked.product_id,
        ranked.variant,
        ranked.thumb,
        ranked.large,
        ranked.hi_res
    FROM (
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
    ) ranked
    WHERE ranked.rn = 1
) ri ON ri.product_id = p.id
ORDER BY p.id ASC
```

`LIMIT :limit`에는 `size + 1` 값을 전달한다.

### 7.3 비권장: 컬렉션 fetch join

```java
select distinct p
from Product p
left join fetch p.images
where (:cursor is null or p.id > :cursor)
order by p.id asc
```

이 방식은 다음 이유로 목록 API에는 적합하지 않다.

- 상품별 image 전체를 가져온다.
- DB row가 image 개수만큼 늘어난다.
- JPA pagination과 함께 사용할 때 결과가 불안정하거나 메모리 pagination이 발생할 수 있다.
- "상품별 첫 번째 image 1개"라는 요구사항을 직접 만족하지 않는다.

---

## 8. Repository 설계

### 8.1 Product 목록 native projection

목록 API는 Product 필드와 대표 이미지 필드를 함께 담는 native projection을 사용한다.

```java
public interface ProductSummaryNativeProjection {
    Long getId();
    String getParentAsin();
    String getTitle();
    String getMainCategory();
    BigDecimal getAverageRating();
    Integer getRatingNumber();
    BigDecimal getPrice();
    String getStore();
    String getImageVariant();
    String getImageThumb();
    String getImageLarge();
    String getImageHiRes();
}
```

DTO 조립 시 `mainCategory` 문자열은 `MainCategory` enum으로 변환한다.

예상 repository 메서드:

```java
@Query(value = """
        SELECT
            p.id AS id,
            p.parent_asin AS parentAsin,
            p.title AS title,
            p.main_category AS mainCategory,
            p.average_rating AS averageRating,
            p.rating_number AS ratingNumber,
            p.price AS price,
            p.store AS store,
            ri.variant AS imageVariant,
            ri.thumb AS imageThumb,
            ri.large AS imageLarge,
            ri.hi_res AS imageHiRes
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
            WHERE (:cursor IS NULL OR p.id > :cursor)
            ORDER BY p.id ASC
            LIMIT :limit
        ) p
        LEFT JOIN (
            SELECT
                ranked.product_id,
                ranked.variant,
                ranked.thumb,
                ranked.large,
                ranked.hi_res
            FROM (
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
            ) ranked
            WHERE ranked.rn = 1
        ) ri ON ri.product_id = p.id
        ORDER BY p.id ASC
        """, nativeQuery = true)
List<ProductSummaryNativeProjection> findProductSummariesWithRepresentativeImage(
        @Param("cursor") Long cursor,
        @Param("limit") int limit
);
```

### 8.2 제거된 별도 ProductImage 조회

대표 이미지는 Product 목록 native query에서 함께 조회한다. 따라서 product ids를 기반으로 `ProductImageRepository`를 별도 호출하지 않는다.

기존 2-step 방식에서 필요했던 projection은 사용하지 않는다.

```java
// 사용하지 않음
public record ProductSummaryProjection(
        Long id,
        String parentAsin,
        String title,
        MainCategory mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store
) {
}
```

```java
// 사용하지 않음
public record ProductImageProjection(
        Long productId,
        String variant,
        String thumb,
        String large,
        String hiRes
) {
}
```

---

## 9. 제안 패키지 구조

```text
jeong.awsshop.product.controller
└── ProductController

jeong.awsshop.product.service.productread
└── ProductReadService

jeong.awsshop.product.service.productread.dto
├── ProductCursorResponse
├── ProductSummaryResponse
├── ProductImageResponse
└── ProductSummaryNativeProjection

jeong.awsshop.product.repository
└── ProductRepository
```

현재 프로젝트에는 `ProductController`, `ProductRepository`가 이미 존재한다. 이 API는 대표 이미지를 Product 목록 native query에서 함께 조회하므로 `ProductRepository`에 조회 메서드를 추가한다.

---

## 10. Controller 설계

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductReadService productReadService;

    @GetMapping
    public ResponseEntity<ProductCursorResponse> getProducts(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cursor
    ) {
        return ResponseEntity.ok(productReadService.getProducts(size, cursor));
    }
}
```

controller 책임:

- HTTP query parameter 수신
- 요청 값 검증 또는 검증 객체 위임
- service 호출
- response 반환

controller가 직접 DB 조회나 DTO 조립을 수행하지 않는다.

---

## 11. Service 설계

```java
@Service
@Transactional(readOnly = true)
public class ProductReadService {

    public ProductCursorResponse getProducts(int size, Long cursor) {
        validate(size, cursor);

        int querySize = size + 1;

        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummariesWithRepresentativeImage(
                        cursor,
                        querySize
                );

        boolean hasNext = rows.size() > size;
        List<ProductSummaryNativeProjection> pageRows = rows.stream()
                .limit(size)
                .toList();

        List<ProductSummaryResponse> products = assemble(pageRows);
        Long nextCursorId = products.isEmpty()
                ? null
                : products.get(products.size() - 1).id();

        return new ProductCursorResponse(products, nextCursorId, hasNext);
    }
}
```

service 책임:

- cursor pagination 정책 적용
- `size + 1` 조회
- native projection을 response DTO로 변환
- `nextCursorId`, `hasNext` 계산

---

## 12. 테스트 기준

### 12.1 Controller 테스트

검증할 내용:

- `GET /api/products` 요청이 service를 호출한다.
- `size` 기본값이 적용된다.
- `cursor`가 없으면 첫 페이지 조회로 전달된다.
- 유효하지 않은 `size`, `cursor`는 `400 Bad Request`를 반환한다.

### 12.2 Service 테스트

검증할 내용:

- `cursor = null`이면 첫 페이지를 조회한다.
- `cursor`가 있으면 `id > cursor` 기준으로 조회한다.
- `size + 1`개를 조회해서 `hasNext`를 판단한다.
- 응답 상품 수는 최대 `size`개다.
- 마지막 응답 상품의 `id`가 `nextCursorId`가 된다.
- 조회 결과가 없으면 `nextCursorId = null`, `hasNext = false`다.
- 대표 image가 native projection에서 올바르게 DTO로 변환된다.
- `MAIN` 이미지가 있으면 `MAIN` 이미지를 우선 반환한다.
- `MAIN` 이미지가 없으면 첫 번째 이미지를 반환한다.
- 이미지가 없으면 `image = null`을 반환한다.

### 12.3 Repository 테스트

검증할 내용:

- `id ASC` 정렬이 보장된다.
- cursor보다 큰 id만 조회된다.
- projection에 필요한 필드만 매핑된다.
- `ROW_NUMBER()`로 product별 대표 image 1장만 조인된다.
- `MAIN` 이미지가 우선 선택된다.

---

## 13. 결정 사항

- Cursor 기준은 `Product.id`로 한다.
- 정렬은 `id ASC`로 한다.
- cursor 조건은 `id > cursor`로 한다.
- 응답의 `nextCursorId`는 응답에 포함된 마지막 Product id로 한다.
- `hasNext` 판단은 `size + 1` 조회 방식으로 한다.
- 목록 조회에서는 fetch join pagination을 사용하지 않는다.
- Product 목록과 대표 image는 native query 1번으로 함께 조회한다.
- API 응답 필드명은 도메인 기준으로 `averageRating`을 사용한다.
- 대표 이미지는 `variant = 'MAIN'`을 우선 사용하고, 없으면 `ProductImage.id ASC` 기준 첫 번째 이미지를 사용한다.
- 응답 필드는 대표 이미지 1장을 의미하므로 `image` 단수로 한다.
- 빈 결과는 `products = []`, `nextCursorId = null`, `hasNext = false`로 반환한다.
- `size` 기본값은 `20`, 최대값은 `100`으로 한다.
- 대표 이미지 조회는 MySQL 8 이상 `ROW_NUMBER()` 기반 native query로 구현한다.
- `ROW_NUMBER()`의 정렬 조건은 `MAIN` 우선, 이후 `ProductImage.id ASC` 순서다.

---

## 14. 대표 이미지 조회 방식 비교 및 결정

대표 이미지 조회 방식은 native query로 최종 결정한다. 아래는 JPQL 방식과 native query 방식을 비교한 내용이다.

### 14.1 JPQL 방식

JPQL 방식은 product ids에 해당하는 모든 이미지를 조회한 뒤 service에서 대표 이미지 1장을 선택한다.

예상 흐름:

```text
Product ids 조회
  -> ProductImage where product_id in (...)
  -> product_id asc, image id asc 정렬
  -> service에서 MAIN 우선, 없으면 첫 번째 image 선택
```

장점:

- JPA와 JPQL만 사용하므로 구현이 단순하다.
- DB 벤더 의존성이 낮다.
- 테스트 작성이 쉽다.
- 현재 repository 구조와 잘 맞는다.

단점:

- 상품별 이미지가 많으면 필요 없는 이미지까지 가져온다.
- API가 대표 이미지 1장만 필요해도 DB 네트워크 전송량이 증가한다.
- service에서 grouping과 대표 이미지 선택 로직이 필요하다.
- 데이터가 커질수록 목록 조회 API의 응답 시간이 이미지 개수에 영향을 받는다.

적합한 경우:

- 상품별 이미지 개수가 적다.
- 빠르게 구현하고 검증하는 것이 우선이다.
- DB 벤더 독립성을 유지하고 싶다.
- 초기 트래픽이 크지 않다.

### 14.2 Native Query 방식

native query 방식은 DB에서 product별 대표 이미지 1장을 직접 선택한다. MySQL 8 이상 `ROW_NUMBER()` window function을 사용한다.

예상 흐름:

```text
Product 목록을 cursor 조건으로 제한
  -> ProductImage에서 product_id별 ranking 계산
  -> rn = 1인 image만 Product 목록에 LEFT JOIN
  -> service는 단일 projection을 DTO로 변환
```

예상 SQL:

```sql
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
    WHERE (:cursor IS NULL OR p.id > :cursor)
    ORDER BY p.id ASC
    LIMIT :limit
) p
LEFT JOIN (
    SELECT
        ranked.product_id,
        ranked.variant,
        ranked.thumb,
        ranked.large,
        ranked.hi_res
    FROM (
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
    ) ranked
    WHERE ranked.rn = 1
) ri ON ri.product_id = p.id
ORDER BY p.id ASC
```

장점:

- DB에서 상품별 대표 이미지 1장만 반환하므로 전송량이 작다.
- service 로직이 단순해진다.
- 상품별 이미지 개수가 많아도 API 성능이 안정적이다.
- 현재 요구사항인 "대표 이미지 1장"에 가장 직접적으로 맞는다.

단점:

- MySQL 8 이상 같은 DB 기능에 의존한다.
- JPQL보다 테스트와 유지보수가 까다롭다.
- native query 결과를 projection에 매핑하는 코드가 필요하다.
- DB 변경 가능성이 있으면 이식성이 떨어진다.

적합한 경우:

- 상품 수와 이미지 수가 많다.
- 목록 API 성능이 중요하다.
- DB가 MySQL 8 이상으로 고정되어 있다.
- 대표 이미지 1장만 필요하다는 요구사항이 안정적이다.

### 14.3 현재 판단

이 API는 대용량 Product 목록 조회를 전제로 한다. 또한 응답에는 대표 이미지 1장만 필요하다.

따라서 native query 방식으로 진행한다.

결정 이유:

- 대표 이미지 1장만 필요하므로 모든 이미지를 가져오는 JPQL 방식은 낭비가 있다.
- `ROW_NUMBER()`로 `MAIN` 이미지에 우선순위를 부여하면 요구사항을 SQL에서 직접 표현할 수 있다.
- `MAIN` 이미지가 없는 경우에도 `ProductImage.id ASC` 첫 번째 row를 안정적으로 선택할 수 있다.
- Product 목록과 대표 image를 하나의 native query projection으로 받을 수 있어 service 조립 로직이 단순하다.
