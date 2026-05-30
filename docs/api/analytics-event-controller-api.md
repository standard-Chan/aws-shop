# AnalyticsEventController API 문서

`AnalyticsEventController`는 사용자 행동 이벤트를 전용 API로 수집하고 Kafka로 발행한다.

- Base URL: `/api/analytics/events`
- Content-Type: `application/json`
- Controller 위치: `src/main/java/jeong/awsshop/analytics/presentation/AnalyticsEventController.java`

## 공통 규칙

### 이벤트 생성 규칙

- 클라이언트는 이벤트 발생 시각을 보내지 않는다.
- 서버가 `SnowflakeIdGenerator`로 `eventId`를 발급한다.
- 서버가 UTC 기준 `occurredAt`을 생성해 Kafka 메시지에 포함한다.
- 성공 응답에는 `eventId`, `eventType`만 반환한다.

### Kafka 발행 규칙

- Kafka key는 `userId` 문자열이다.
- Kafka value는 이벤트 공통 메시지 형식으로 발행된다.
- 이벤트 타입별 topic은 설정으로 변경할 수 있으며, 기본값은 아래 API별 설명을 따른다.

### 요청 바디 검증

- `userId`, `productId`, `orderId`는 필수이며 양수여야 한다.
- 검색 이벤트의 `keyword`는 필수이며 blank일 수 없다.
- 상품 조회 이벤트의 `searchEventId`는 선택이며, 값이 있으면 양수여야 한다.
- 상품 조회 이벤트의 `searchKeyword`는 선택이며, 값이 있으면 blank일 수 없다.
- 요청 검증 실패 시 `400 Bad Request`를 반환한다.

### 예외 응답

| 상태 코드 | 설명 |
| --- | --- |
| `202 Accepted` | 이벤트 수집 요청을 검증하고 Kafka 발행 완료 |
| `400 Bad Request` | 요청 본문 누락, 필수 필드 누락, blank keyword/searchKeyword, 0 이하 ID |
| `503 Service Unavailable` | Kafka 발행 실패 |

## 1. 검색 이벤트 수집

### 요청

- Method: `POST`
- URL: `/api/analytics/events/search`
- Kafka topic 기본값: `search-events`
- Event Type: `SEARCH`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | long | Y | 사용자 ID |
| `keyword` | string | Y | 검색 키워드 |

### 요청 예시

```json
{
  "userId": 1,
  "keyword": "macbook"
}
```

### 응답 예시

```json
{
  "eventId": 123,
  "eventType": "SEARCH"
}
```

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/analytics/events/search' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "keyword": "macbook"
  }'
```

## 2. 상품 상세 조회 이벤트 수집

### 요청

- Method: `POST`
- URL: `/api/analytics/events/product-view`
- Kafka topic 기본값: `product-view-events`
- Event Type: `PRODUCT_VIEW`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | long | Y | 사용자 ID |
| `productId` | long | Y | 조회한 상품 ID |
| `searchEventId` | long | N | 이 상품 조회로 이어진 검색 이벤트 ID |
| `searchKeyword` | string | N | 이 상품 조회로 이어진 검색어 |

`searchEventId`는 값이 있으면 양수여야 한다. `searchKeyword`는 값이 있으면 blank일 수 없다.
수집 API에서는 `searchEventId`와 `searchKeyword`가 실제 검색 이벤트와 일치하는지 DB로 검증하지 않는다.
빠른 이벤트 수집을 우선하고, 정합성 평가는 후속 분석/데이터 품질 작업에서 처리한다.

### 요청 예시

```json
{
  "userId": 1,
  "productId": 100,
  "searchEventId": 123,
  "searchKeyword": "macbook"
}
```

기존 요청도 계속 허용한다.

```json
{
  "userId": 1,
  "productId": 100
}
```

### 응답 예시

```json
{
  "eventId": 124,
  "eventType": "PRODUCT_VIEW"
}
```

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/analytics/events/product-view' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "productId": 100,
    "searchEventId": 123,
    "searchKeyword": "macbook"
  }'
```

## 3. 장바구니 추가 이벤트 수집

### 요청

- Method: `POST`
- URL: `/api/analytics/events/add-to-cart`
- Kafka topic 기본값: `cart-events`
- Event Type: `ADD_TO_CART`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | long | Y | 사용자 ID |
| `productId` | long | Y | 장바구니에 추가한 상품 ID |

### 요청 예시

```json
{
  "userId": 1,
  "productId": 100
}
```

### 응답 예시

```json
{
  "eventId": 125,
  "eventType": "ADD_TO_CART"
}
```

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/analytics/events/add-to-cart' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "productId": 100
  }'
```

## 4. 구매 이벤트 수집

### 요청

- Method: `POST`
- URL: `/api/analytics/events/purchase`
- Kafka topic 기본값: `purchase-events`
- Event Type: `PURCHASE`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | long | Y | 사용자 ID |
| `orderId` | long | Y | 구매 완료 주문 ID |

### 요청 예시

```json
{
  "userId": 1,
  "orderId": 500
}
```

### 응답 예시

```json
{
  "eventId": 126,
  "eventType": "PURCHASE"
}
```

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/analytics/events/purchase' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "orderId": 500
  }'
```

## Kafka 메시지 형식

API 응답에는 포함하지 않지만, Kafka에는 아래 공통 구조로 발행된다.

```json
{
  "eventId": 123,
  "eventType": "SEARCH",
  "userId": 1,
  "occurredAt": "2026-05-29T03:00:00Z",
  "keyword": "macbook",
  "productId": null,
  "orderId": null,
  "searchEventId": null
}
```

상품 조회 이벤트가 검색에서 이어졌다면 `keyword`에는 `searchKeyword`가 들어가고, `searchEventId`에는 연결 대상 검색 이벤트 ID가 들어간다.

```json
{
  "eventId": 124,
  "eventType": "PRODUCT_VIEW",
  "userId": 1,
  "occurredAt": "2026-05-29T03:00:10Z",
  "keyword": "macbook",
  "productId": 100,
  "orderId": null,
  "searchEventId": 123
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `eventId` | long | 서버가 발급한 이벤트 ID |
| `eventType` | string | `SEARCH`, `PRODUCT_VIEW`, `ADD_TO_CART`, `PURCHASE` |
| `userId` | long | 사용자 ID |
| `occurredAt` | string | 서버 기준 이벤트 수집 시각, UTC ISO-8601 |
| `keyword` | string or `null` | `SEARCH.keyword` 또는 `PRODUCT_VIEW.searchKeyword` |
| `productId` | long or `null` | 상품 조회/장바구니 이벤트의 상품 ID |
| `orderId` | long or `null` | 구매 이벤트의 주문 ID |
| `searchEventId` | long or `null` | 상품 조회 이벤트가 이어진 검색 이벤트 ID |
