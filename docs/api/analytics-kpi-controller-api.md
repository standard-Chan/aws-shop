# AnalyticsKpiController API 문서

`AnalyticsKpiController`는 `analytics_events` 테이블에 적재된 사용자 행동 이벤트를 이벤트 수 기준 KPI로 집계한다.

- Base URL: `/api/analytics/kpis`
- Controller 위치: `src/main/java/jeong/awsshop/analytics/presentation/AnalyticsKpiController.java`
- 기준: `EVENT_COUNT`

## 공통 규칙

- `from`, `to`는 필수 ISO-8601 Instant 문자열이다.
- 조회 조건은 `occurred_at >= from AND occurred_at < to`다.
- `from >= to`이면 `400 Bad Request`를 반환한다.
- 목록 API의 `limit` 기본값은 `20`, 허용 범위는 `1..100`이다.
- rate 계산에서 분모가 `0`이면 `0.0`을 반환한다.

## 1. 전체 요약 KPI

### 요청

- Method: `GET`
- URL: `/api/analytics/kpis/summary`

### 호출 예시

```bash
curl 'http://localhost:8080/api/analytics/kpis/summary?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z'
```

### 응답 예시

```json
{
  "from": "2026-05-01T00:00:00Z",
  "to": "2026-06-01T00:00:00Z",
  "basis": "EVENT_COUNT",
  "searchCount": 100,
  "productViewCount": 40,
  "addToCartCount": 12,
  "purchaseCount": 3,
  "searchCtr": 0.4,
  "cartRate": 0.3,
  "purchaseRate": 0.075
}
```

계산 규칙:

- `searchCtr = productViewCount / searchCount`
- `cartRate = addToCartCount / productViewCount`
- `purchaseRate = purchaseCount / productViewCount`

## 2. 상품별 KPI

### 요청

- Method: `GET`
- URL: `/api/analytics/kpis/products`

### 호출 예시

```bash
curl 'http://localhost:8080/api/analytics/kpis/products?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z&limit=20'
```

### 응답 예시

```json
{
  "from": "2026-05-01T00:00:00Z",
  "to": "2026-06-01T00:00:00Z",
  "basis": "EVENT_COUNT",
  "items": [
    {
      "productId": 100,
      "productViewCount": 40,
      "addToCartCount": 12,
      "cartRate": 0.3,
      "purchaseRate": null
    }
  ]
}
```

계산/정렬 규칙:

- `productViewCount`: `PRODUCT_VIEW` 이벤트를 `productId` 기준으로 집계한다.
- `addToCartCount`: `ADD_TO_CART` 이벤트를 `productId` 기준으로 집계한다.
- `cartRate = addToCartCount / productViewCount`
- `purchaseRate`: V1에서는 `null`이다.
- 정렬은 `productViewCount DESC`, `productId ASC`다.

## 3. 검색어별 KPI

### 요청

- Method: `GET`
- URL: `/api/analytics/kpis/keywords`

### 호출 예시

```bash
curl 'http://localhost:8080/api/analytics/kpis/keywords?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z&limit=20'
```

### 응답 예시

```json
{
  "from": "2026-05-01T00:00:00Z",
  "to": "2026-06-01T00:00:00Z",
  "basis": "EVENT_COUNT",
  "items": [
    {
      "keyword": "macbook",
      "searchCount": 100,
      "productViewCount": 30,
      "searchCtr": 0.3
    }
  ]
}
```

계산/정렬 규칙:

- `searchCount`: `SEARCH.keyword` 기준으로 집계한다.
- `productViewCount`: `PRODUCT_VIEW.keyword` 기준으로 집계한다.
- `PRODUCT_VIEW.keyword`가 null인 이벤트는 검색어별 조회 수에 포함하지 않는다.
- `searchCtr = productViewCount / searchCount`
- 정렬은 `searchCount DESC`, `keyword ASC`다.
