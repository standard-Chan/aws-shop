# Product 상세 조회 Query 전략 의도 문서

## 요약

`GET /api/products/{id}` 상세 조회는 Product 본문과 여러 child collection을 한 번의 multi join으로 가져오지 않고, Product 본문 1회 조회 후 child collection별로 분리 조회한다. 의도는 여러 1:N 관계를 동시에 join할 때 발생하는 row 폭발을 피하고, 각 collection의 정렬과 DTO 조립을 단순하게 유지하기 위함이다.

---

## 1. 배경

Product 상세 응답은 단일 Product 본문뿐 아니라 아래 child collection 전체를 포함한다.

- `features`
- `descriptions`
- `categories`
- `boughtTogether`
- `images`
- `videos`

각 child는 Product와 1:N 관계다. 즉, 상품 1개에 대해 각 collection이 여러 row를 가질 수 있다.

---

## 2. 선택한 방식

현재 구현은 아래 순서로 조회한다.

1. `product` 본문 projection을 `id`로 조회한다.
2. `product_features`를 `product_id`로 조회한다.
3. `product_descriptions`를 `product_id`로 조회한다.
4. `product_categories`를 `product_id`로 조회한다.
5. `product_bought_together`를 `product_id`로 조회한다.
6. `product_images`를 `product_id`로 조회한다.
7. `product_videos`를 `product_id`로 조회한다.
8. Service에서 `ProductDetailResponse`로 조립한다.

쿼리 수는 많아지지만, 상세 조회 1건 기준으로 고정된 수의 쿼리만 발생한다.

---

## 3. multi join을 피한 이유

여러 1:N collection을 한 SQL에서 join하면 결과 row 수가 곱으로 증가한다.

예를 들어 한 Product가 아래 데이터를 가진다고 가정한다.

- feature 5개
- description 3개
- category 4개
- image 8개
- video 2개

단순 join 결과는 상품 1개가 아니라 다음 개수의 row로 늘어난다.

```text
5 * 3 * 4 * 8 * 2 = 960 rows
```

이 경우 문제는 다음과 같다.

- DB가 중복 조합 row를 많이 만든다.
- 네트워크로 전송되는 데이터가 커진다.
- 애플리케이션에서 같은 feature, image, category를 중복 제거해야 한다.
- 각 collection의 독립 정렬을 유지하기 어렵다.
- JPA fetch join으로 여러 bag collection을 한 번에 가져오는 방식은 제약과 중복 문제가 크다.

따라서 Product 상세 조회에서는 multi join보다 collection별 분리 조회가 더 예측 가능하다.

---

## 4. JSON aggregation 대안

MySQL 8의 `JSON_ARRAYAGG`, `JSON_OBJECT`를 사용하면 Product row 1개에 child collection을 JSON 배열로 묶어 넣을 수 있다.

개념 예시:

```sql
SELECT
    p.id,
    p.title,
    (
        SELECT JSON_ARRAYAGG(
            JSON_OBJECT(
                'featureIndex', pf.feature_index,
                'feature', pf.feature
            )
        )
        FROM product_features pf
        WHERE pf.product_id = p.id
    ) AS features
FROM product p
WHERE p.id = :id;
```

이 방식은 DB round trip을 1번으로 줄일 수 있다. 하지만 이번 GREEN 구현에서는 선택하지 않았다.

선택하지 않은 이유:

- SQL이 길고 DB 함수에 강하게 의존한다.
- 각 child collection별 정렬을 JSON aggregation 내부에서 명확히 보장해야 한다.
- projection 결과가 JSON 문자열이 되므로 애플리케이션에서 다시 파싱해야 한다.
- 테스트와 디버깅이 복잡해진다.
- 현재 RED 테스트의 핵심은 상세 조회 계약을 빠르게 GREEN으로 만드는 것이다.

JSON aggregation은 성능 병목이 확인되면 검토할 수 있는 최적화 후보로 남긴다.

---

## 5. 현재 방식의 장점

- Product와 child collection의 조회 책임이 명확하다.
- 각 child repository가 자기 collection의 정렬을 독립적으로 보장한다.
- row 폭발이 발생하지 않는다.
- DTO 조립이 단순하다.
- 테스트에서 Product 본문, feature 정렬, description 정렬, image MAIN 우선 정책을 각각 검증하기 쉽다.
- 단일 상세 조회 기준으로 query 수가 고정되어 예측 가능하다.

---

## 6. 현재 방식의 단점

- DB round trip이 여러 번 발생한다.
- Product 상세 조회가 매우 자주 호출되거나 DB latency가 커지면 성능 비용이 커질 수 있다.
- child collection이 추가될 때 repository query도 추가될 가능성이 높다.

---

## 7. 결정

현재 단계에서는 Product 상세 조회를 여러 쿼리로 분리해서 구현한다.

결정 근거:

- 여러 1:N join의 row 폭발을 피한다.
- GREEN 단계에서는 단순하고 검증 가능한 구현을 우선한다.
- child collection별 정렬 정책을 명확히 유지한다.
- 성능 병목이 실제로 확인되기 전까지 JSON aggregation 최적화는 도입하지 않는다.

추후 상세 조회 성능이 문제가 되면 다음 대안을 검토한다.

- MySQL JSON aggregation으로 단일 row 응답 구성
- child collection 일부를 묶는 hybrid query
- read model 또는 cache 도입
