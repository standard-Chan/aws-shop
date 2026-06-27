# Product Ranking Load Test Compose

nginx 1대가 `product-ranking` 서버 2대에 요청을 분산하는 부하테스트용 Docker Compose 구성이다.

## 실행

```bash
docker compose -f event-pipeline/product-ranking-load-test/docker-compose.yml up --build
```

nginx 공개 URL은 기본 `http://localhost:18083`이다.

```bash
curl -X POST http://localhost:18083/api/event-pipeline/product-ranking/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":1,"eventType":"PRODUCT_VIEW","userId":1,"occurredAt":"2026-06-07T06:00:00Z","keyword":null,"productId":100,"orderId":null,"searchEventId":null}'
```

```bash
BASE_URL=http://localhost:18083 \
k6 run k6/product-ranking/product-ranking-events.js
```

## 구성

- `nginx`: 단일 부하테스트 URL을 제공하고 `least_conn`으로 요청을 분산한다.
- `product-ranking-1`, `product-ranking-2`: 동일한 product-ranking 애플리케이션 인스턴스다.
- `redis`: 두 인스턴스가 공유하는 실시간 랭킹 저장소다.
- `clickhouse`: 두 인스턴스가 공유하는 장기 윈도우 랭킹 저장소다.

## 환경변수

- `PRODUCT_RANKING_LB_PORT`: nginx host port. 기본값은 `18083`.
- `PRODUCT_RANKING_REDIS_PORT`: Redis host port. 기본값은 `16379`.
- `PRODUCT_RANKING_CLICKHOUSE_HTTP_PORT`: ClickHouse HTTP host port. 기본값은 `18123`.
- `PRODUCT_RANKING_CLICKHOUSE_NATIVE_PORT`: ClickHouse native host port. 기본값은 `19000`.
- `EVENT_PRODUCT_RANKING_CLICKHOUSE_ENABLED`: ClickHouse 적재 활성화 여부. 기본값은 `true`.
- `EVENT_PRODUCT_RANKING_BATCH_SIZE`: batch 크기. 기본값은 `1000`.
- `EVENT_PRODUCT_RANKING_BATCH_FLUSH_INTERVAL_MILLIS`: flush 주기. 기본값은 `1000`.
- `EVENT_PRODUCT_RANKING_BATCH_QUEUE_CAPACITY`: 인스턴스별 queue 용량. 기본값은 `100000`.
- `PRODUCT_RANKING_JAVA_TOOL_OPTIONS`: product-ranking JVM 옵션. 기본값은 `-XX:MaxRAMPercentage=75`.

## 종료

```bash
docker compose -f event-pipeline/product-ranking-load-test/docker-compose.yml down
```
