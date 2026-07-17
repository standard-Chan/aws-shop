# Product 상세 캐시 비동기 저장 구현 계획

## 1. 목적

Redis MISS 후 DB 조회가 성공했을 때 Redis 저장 완료를 기다리지 않고 즉시 상품 상세 응답을 반환한다.
Redis 저장은 비동기 best-effort 작업으로 처리한다.

## 2. 핵심 동작

- 캐시 OFF: 기존처럼 DB 조회만 수행한다.
- 캐시 ON + HIT: Redis 조회 결과를 즉시 반환하고 TTL 갱신 정책은 유지한다.
- 캐시 ON + MISS: DB 조회 후 async writer에 Redis 저장을 맡기고 즉시 응답한다.
- DB 조회 404: async writer를 호출하지 않는다.
- Redis 저장 실패: warn 로그만 남기고 API 응답에는 영향을 주지 않는다.

## 3. 구현 구조

- `ProductReadService`
  - MISS 시 `ProductDetailCacheAsyncWriter.saveAsync(id, response)`를 호출한다.
  - `ProductDetailCacheRepository.save(...)`를 직접 호출하지 않는다.
- `ProductDetailCacheAsyncWriter`
  - `@Async("productDetailCacheTaskExecutor")`로 Redis 저장을 요청 thread 밖에서 실행한다.
  - 내부에서 `ProductDetailCacheRepository.save(...)`를 호출한다.
  - 예외는 writer 내부에서 잡아 warn 로그로 처리한다.
- `ProductDetailCacheConfig`
  - `@EnableAsync`를 추가한다.
  - `productDetailCacheTaskExecutor` bean을 제공한다.
- `ProductDetailCacheProperties`
  - async executor 설정을 바인딩한다.

## 4. 설정

```yaml
app:
  product:
    detail-cache:
      async:
        core-pool-size: ${PRODUCT_DETAIL_CACHE_ASYNC_CORE_POOL_SIZE:2}
        max-pool-size: ${PRODUCT_DETAIL_CACHE_ASYNC_MAX_POOL_SIZE:8}
        queue-capacity: ${PRODUCT_DETAIL_CACHE_ASYNC_QUEUE_CAPACITY:1000}
```

`.env`, `.env.example`에는 아래 값을 추가한다.

```properties
PRODUCT_DETAIL_CACHE_ASYNC_CORE_POOL_SIZE=2
PRODUCT_DETAIL_CACHE_ASYNC_MAX_POOL_SIZE=8
PRODUCT_DETAIL_CACHE_ASYNC_QUEUE_CAPACITY=1000
```

## 5. 테스트 계획

- `ProductReadService` 테스트
  - MISS 시 DB 응답을 반환하고 async writer를 호출한다.
  - MISS 시 cache repository `save`를 직접 호출하지 않는다.
  - 404 시 async writer를 호출하지 않는다.
  - 캐시 OFF면 cache read/write 모두 호출하지 않는다.
- `ProductDetailCacheAsyncWriter` 테스트
  - `saveAsync`가 repository `save`를 호출한다.
  - repository 저장 예외가 writer 밖으로 전파되지 않는다.
- 기존 Redis repository 조회, TTL 갱신, 저장 테스트는 유지한다.
- 최종 검증은 `./gradlew :test`로 수행한다.
