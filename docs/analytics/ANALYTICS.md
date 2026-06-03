# Analytics

## V1 Scope

V1은 사용자 행동 이벤트를 전용 API로 수집하고 Kafka topic으로 발행한 뒤, Consumer가 analytics 전용 테이블에 적재하는 단계다.

- 기존 상품/주문/결제 API에는 자동 이벤트 발행을 붙이지 않는다.
- Funnel 조회 API와 KPI 조회 API를 구현했다.
- Analytics DB는 우선 같은 datasource 안의 analytics 전용 테이블로 논리 분리하고, 필요 시 물리 DB 분리로 확장한다.

## API Contract

공통 성공 응답은 `202 Accepted`와 아래 JSON이다.

```json
{
  "eventId": 123,
  "eventType": "SEARCH"
}
```

Kafka 발행 실패 시 `503 Service Unavailable`을 반환한다.

### Search

`POST /api/analytics/events/search`

```json
{
  "userId": 1,
  "keyword": "macbook"
}
```

- `userId`: 필수, 양수
- `keyword`: 필수, blank 불가
- Kafka topic: `search-events`

### Product View

`POST /api/analytics/events/product-view`

```json
{
  "userId": 1,
  "productId": 100,
  "searchEventId": 123,
  "searchKeyword": "macbook"
}
```

- `userId`: 필수, 양수
- `productId`: 필수, 양수
- `searchEventId`: 선택, 값이 있으면 양수
- `searchKeyword`: 선택, 값이 있으면 blank 불가
- Kafka topic: `product-view-events`

기존 요청도 계속 허용한다.

```json
{
  "userId": 1,
  "productId": 100
}
```

수집 API는 빠른 이벤트 수집을 우선하므로 `searchEventId`와 `searchKeyword`가 실제 검색 이벤트와 일치하는지 DB로 검증하지 않는다.

### Add To Cart

`POST /api/analytics/events/add-to-cart`

```json
{
  "userId": 1,
  "productId": 100
}
```

- `userId`: 필수, 양수
- `productId`: 필수, 양수
- Kafka topic: `cart-events`

### Purchase

`POST /api/analytics/events/purchase`

```json
{
  "userId": 1,
  "orderId": 500
}
```

- `userId`: 필수, 양수
- `orderId`: 필수, 양수
- Kafka topic: `purchase-events`

## Kafka Message

서버가 `SnowflakeIdGenerator`로 `eventId`를 발급하고, `Clock.systemUTC()` 기준으로 `occurredAt`을 생성한다. 클라이언트 timestamp는 V1에서 받지 않는다.

Kafka key는 `userId` 문자열이다.

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

`eventType` 값은 `SEARCH`, `PRODUCT_VIEW`, `ADD_TO_CART`, `PURCHASE`로 고정한다.

상품 조회 이벤트가 검색에서 이어진 경우 `searchKeyword`는 메시지의 `keyword`에 저장하고, 연결 대상 검색 이벤트 ID는 `searchEventId`에 저장한다.

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

## Kafka Consumer

Consumer는 이벤트 타입별 topic을 구독하고 같은 저장 서비스로 위임한다.

- `search-events`
- `product-view-events`
- `cart-events`
- `purchase-events`

기본 consumer group은 `analytics-event-consumer-group`이다.

중복 메시지는 `eventId` 기준으로 처리한다. 이미 같은 `eventId`가 `analytics_events`에 있으면 정상 처리로 보고 추가 저장하지 않는다.

역직렬화 실패와 DLT는 아직 구현하지 않았다. Consumer 설정은 `ErrorHandlingDeserializer`를 사용하지만, 실패 메시지 재처리와 dead-letter topic 라우팅은 후속 작업으로 둔다.

## Analytics Events Table

Consumer는 Kafka 메시지를 `analytics_events` 단일 테이블에 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| `event_id` | 서버가 발급한 이벤트 ID, PK |
| `event_type` | `SEARCH`, `PRODUCT_VIEW`, `ADD_TO_CART`, `PURCHASE` |
| `user_id` | 이벤트를 발생시킨 사용자 ID |
| `occurred_at` | 서버가 이벤트 수집 시 생성한 발생 시각 |
| `keyword` | 검색 이벤트 키워드 또는 상품 조회 이벤트의 검색어 컨텍스트 |
| `product_id` | 상품 조회/장바구니 이벤트 상품 ID |
| `order_id` | 구매 이벤트 주문 ID |
| `search_event_id` | 상품 조회 이벤트가 이어진 검색 이벤트 ID |
| `created_at` | Consumer가 DB에 저장한 시각 |

현재 프로젝트는 Flyway/Liquibase를 쓰지 않는다. `dev/test`는 JPA ddl-auto에 맡기고, `prod`는 `ddl-auto: validate`이므로 운영 DB에는 아래 DDL을 먼저 적용해야 한다.

```sql
CREATE TABLE analytics_events (
    event_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    user_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    keyword VARCHAR(255),
    product_id BIGINT,
    order_id BIGINT,
    search_event_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_analytics_events_type_occurred_at
    ON analytics_events (event_type, occurred_at);

CREATE INDEX idx_analytics_events_user_occurred_at
    ON analytics_events (user_id, occurred_at);

CREATE INDEX idx_analytics_events_search_event_id
    ON analytics_events (search_event_id);

CREATE INDEX idx_analytics_events_type_product_occurred_at
    ON analytics_events (event_type, product_id, occurred_at);

CREATE INDEX idx_analytics_events_type_keyword_occurred_at
    ON analytics_events (event_type, keyword, occurred_at);
```

## Local Kafka

기본 bootstrap server는 `localhost:9092`다.

```bash
docker compose up -d kafka
```

환경 변수로 topic과 bootstrap server를 바꿀 수 있다.

```properties
ANALYTICS_KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
ANALYTICS_TOPIC_SEARCH=search-events
ANALYTICS_TOPIC_PRODUCT_VIEW=product-view-events
ANALYTICS_TOPIC_CART=cart-events
ANALYTICS_TOPIC_PURCHASE=purchase-events
ANALYTICS_CONSUMER_GROUP_ID=analytics-event-consumer-group
```

`ANALYTICS_KAFKA_ENABLED=false`로 설정하면 Kafka Producer/Consumer를 비활성화한다.
이때 Analytics 이벤트 수집 API는 이벤트 ID를 생성해 `202 Accepted`를 반환하지만 Kafka 발행과 Consumer 저장은 수행하지 않는다.

## Funnel Analysis

V1 Funnel 조회는 `analytics_events`에 저장된 이벤트 수를 기준으로 계산한다.

- API: `GET /api/analytics/funnel`
- 기간 조건: `from`, `to` 필수
- 시간 형식: ISO-8601 Instant 문자열
- 조회 조건: `occurred_at >= from AND occurred_at < to`
- 기준: `EVENT_COUNT`

```http
GET /api/analytics/funnel?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z
```

응답 예시:

```json
{
  "from": "2026-05-01T00:00:00Z",
  "to": "2026-06-01T00:00:00Z",
  "basis": "EVENT_COUNT",
  "steps": [
    {
      "eventType": "SEARCH",
      "count": 10000,
      "conversionRateFromPrevious": null,
      "conversionRateFromSearch": 1.0
    },
    {
      "eventType": "PRODUCT_VIEW",
      "count": 4000,
      "conversionRateFromPrevious": 0.4,
      "conversionRateFromSearch": 0.4
    },
    {
      "eventType": "ADD_TO_CART",
      "count": 1200,
      "conversionRateFromPrevious": 0.3,
      "conversionRateFromSearch": 0.12
    },
    {
      "eventType": "PURCHASE",
      "count": 300,
      "conversionRateFromPrevious": 0.25,
      "conversionRateFromSearch": 0.03
    }
  ]
}
```

퍼널 단계는 `SEARCH -> PRODUCT_VIEW -> ADD_TO_CART -> PURCHASE` 순서로 고정한다.

- `conversionRateFromPrevious`: 현재 단계 count / 직전 단계 count
- `conversionRateFromSearch`: 현재 단계 count / SEARCH count
- 분모가 `0`이면 `0.0`으로 반환한다.
- `SEARCH.conversionRateFromPrevious`는 `null`이다.

## KPI Analysis

KPI 조회는 `analytics_events`에 저장된 이벤트 수를 기준으로 계산한다.

- 기준: `EVENT_COUNT`
- 기간 조건: `occurred_at >= from AND occurred_at < to`
- `from >= to`이면 `400 Bad Request`
- 목록 API의 `limit` 기본값은 `20`, 허용 범위는 `1..100`
- rate 계산에서 분모가 `0`이면 `0.0`을 반환한다.

### Summary KPI

`GET /api/analytics/kpis/summary`

```http
GET /api/analytics/kpis/summary?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z
```

응답 필드:

- `searchCount`
- `productViewCount`
- `addToCartCount`
- `purchaseCount`
- `searchCtr = productViewCount / searchCount`
- `cartRate = addToCartCount / productViewCount`
- `purchaseRate = purchaseCount / productViewCount`

응답 예시:

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

### Product KPI

`GET /api/analytics/kpis/products`

상품별 `PRODUCT_VIEW`, `ADD_TO_CART` 이벤트 수를 집계한다.

- 정렬: `productViewCount DESC`, `productId ASC`
- `cartRate = addToCartCount / productViewCount`
- `purchaseRate`는 V1에서 `null`이다.

응답 예시:

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

### Keyword KPI

`GET /api/analytics/kpis/keywords`

검색어별 `SEARCH.keyword`, `PRODUCT_VIEW.keyword` 이벤트 수를 집계한다. `PRODUCT_VIEW.keyword`가 null인 이벤트는 검색어별 조회 수에 포함하지 않는다.

- 정렬: `searchCount DESC`, `keyword ASC`
- `searchCtr = productViewCount / searchCount`

응답 예시:

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

## Documents And Test Calls

- 이벤트 API 문서: `docs/api/analytics-event-controller-api.md`
- KPI API 문서: `docs/api/analytics-kpi-controller-api.md`
- 통합 호출 예시: `src/test/analytics/analytics-api-test.http`
- 기존 HTTP 예시: `src/test/http/analytics/*.http`

검증 명령:

```bash
./gradlew test --tests '*Analytics*' --no-daemon -Dorg.gradle.cache.internal.locklistener=false
./gradlew test --no-daemon -Dorg.gradle.cache.internal.locklistener=false
```
