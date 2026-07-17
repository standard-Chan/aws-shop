# Product 상세 조회 Redis 캐시 구현 계획

## 1. 목적

`GET /api/products/{id}` 상세 조회에서 반복 요청이 DB를 매번 점유하지 않도록 Redis 캐시를 추가한다.
Redis가 꺼져 있으면 기존처럼 DB에서 바로 조회한다.

## 2. 핵심 동작

- `PRODUCT_DETAIL_CACHE_ENABLED=false`: 캐시를 보지 않고 DB 조회
- `PRODUCT_DETAIL_CACHE_ENABLED=true`: Redis 조회 후 HIT면 즉시 반환
- MISS: DB 조회 후 Redis 저장
- HIT: 조회 시점 기준으로 TTL 재설정
- Redis 장애: DB fallback 또는 캐시 저장 생략
- 404 결과: 캐싱하지 않음

## 3. 설정

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  product:
    detail-cache:
      enabled: ${PRODUCT_DETAIL_CACHE_ENABLED:false}
      ttl: ${PRODUCT_DETAIL_CACHE_TTL:1h}
```

`.env`, `.env.example`에는 아래 값을 둔다.

```properties
PRODUCT_DETAIL_CACHE_ENABLED=false
PRODUCT_DETAIL_CACHE_TTL=1h
REDIS_HOST=localhost
REDIS_PORT=6379
```

## 4. 구현 구조

- `ProductReadService`
  - 상세 조회 캐시 ON/OFF와 HIT/MISS 흐름을 오케스트레이션한다.
  - 캐시 OFF면 `ProductDetailCacheRepository`를 호출하지 않는다.
- `ProductDetailDbReader`
  - 기존 상세 DB 조회와 응답 조립을 담당한다.
  - `@Transactional(readOnly = true)`를 가진다.
- `ProductDetailCacheRepository`
  - 상세 캐시 저장소 interface다.
- `RedisProductDetailCacheRepository`
  - Redis 기반 구현체다.
  - key는 `product:detail:{productId}`를 사용한다.
  - `findByProductId`에서 값을 읽으며 TTL을 갱신한다.
  - Redis 예외는 warn 로그 후 fallback 가능하게 처리한다.
- `ProductDetailCacheProperties`
  - `enabled`, `ttl` 설정을 바인딩한다.

## 5. 테스트 계획

- 캐시 OFF이면 cache repository를 호출하지 않고 DB reader를 호출한다.
- 캐시 ON + HIT이면 DB reader를 호출하지 않는다.
- 캐시 ON + MISS이면 DB reader를 호출하고 Redis에 저장한다.
- DB reader가 `ProductNotFoundException`을 던지면 Redis에 저장하지 않는다.
- Redis repository는 값 조회 시 TTL을 갱신한다.
- Redis repository는 조회/저장 예외를 전파하지 않는다.
- 기존 controller 응답 JSON, 400, 404 계약은 유지한다.
- 최종 검증은 `./gradlew test`로 수행한다.
