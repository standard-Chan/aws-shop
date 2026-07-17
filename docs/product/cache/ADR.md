# Product 상세 조회 Redis 캐시 ADR

## Status

Accepted

## Context

현재 `GET /api/products/{id}`는 요청마다 MySQL에서 Product 본문과 child collection을 각각 조회한다.
상세 응답 하나를 만들기 위해 여러 repository 조회가 필요하므로 트래픽이 늘면 DB 부하와 connection 점유 시간이 커질 수 있다.

상품 상세 데이터는 조회 빈도가 높고 변경 빈도는 낮은 read model에 가깝다.
따라서 product id 기준 캐시를 두면 반복 조회에서 DB 접근을 줄일 수 있다.

## Decision

상품 상세 조회 응답 전체인 `ProductDetailResponse`를 Redis에 캐싱한다.

- 캐시 key: `product:detail:{productId}`
- 캐시 value: `ProductDetailResponse` JSON
- 기본 TTL: 1시간
- TTL 설정: `PRODUCT_DETAIL_CACHE_TTL`
- 캐시 ON/OFF 설정: `PRODUCT_DETAIL_CACHE_ENABLED`
- Redis 접속 설정: `REDIS_HOST`, `REDIS_PORT`

Redis 캐시는 기본 OFF로 둔다.
`PRODUCT_DETAIL_CACHE_ENABLED=true`일 때만 상세 조회에서 캐시를 먼저 확인한다.
OFF 상태에서는 캐시 repository를 호출하지 않고 바로 DB 조회 경로로 간다.

캐시 HIT 시에는 값을 반환하면서 TTL을 다시 설정값으로 연장한다.
캐시 MISS 시에는 DB에서 상세 응답을 조립한 뒤 Redis에 저장한다.
존재하지 않는 상품의 404 결과는 캐싱하지 않는다.

Redis 장애는 API 실패로 노출하지 않는다.
캐시 조회 또는 저장 실패는 warn 로그만 남기고 DB 조회 또는 정상 응답을 유지한다.

## Rationale

### DB connection 점유를 줄인다

캐시 HIT 경로는 DB transaction을 열지 않고 응답할 수 있다.
상세 조회 DB 조립 로직을 별도 reader service로 분리하면 HIT 시 connection 점유를 피하는 구조가 명확해진다.

### 캐시 구현을 교체 가능하게 둔다

상세 조회 service는 `ProductDetailCacheRepository` interface에만 의존한다.
Redis 구현은 이 interface를 구현하므로 이후 다른 캐시 저장소로 교체할 수 있다.

### 운영 가용성을 우선한다

Redis는 source of truth가 아니라 성능 최적화 계층이다.
Redis 장애로 상품 상세 API가 실패하면 캐시 도입이 전체 가용성을 낮출 수 있으므로 DB fallback을 선택한다.

### ON/OFF로 점진 도입한다

캐시를 기본 OFF로 두면 Redis 인프라가 준비되지 않은 환경에서도 기존 상세 조회가 그대로 동작한다.
운영 또는 부하테스트 환경에서 환경변수만 바꿔 캐시를 활성화할 수 있다.

## Consequences

### Positive

- 반복 상세 조회의 DB 부하를 줄일 수 있다.
- 캐시 HIT 시 DB connection을 점유하지 않는다.
- Redis 장애 시에도 기존 DB 조회 경로로 응답할 수 있다.
- 환경변수로 캐시 적용 여부와 TTL을 조정할 수 있다.

### Negative

- Redis 의존성과 설정이 추가된다.
- 상품 데이터가 DB에서 변경된 직후 최대 TTL 동안 오래된 상세 응답이 반환될 수 있다.
- 캐시 직렬화 구조가 `ProductDetailResponse` 필드 변경에 영향을 받는다.

## Alternatives Considered

### Spring Cache 추상화 사용

`@Cacheable`을 사용하면 구현량은 줄어든다.
하지만 HIT 시 TTL 연장, Redis 장애 fallback, 캐시 OFF 시 repository 미호출 같은 정책을 명시적으로 제어하기 어렵다.
따라서 repository interface와 Redis 구현체를 직접 둔다.

### 상품 본문과 child collection을 각각 캐싱

세부 데이터 단위로 캐싱하면 일부 데이터 재사용 가능성은 있다.
하지만 상세 API는 완성된 응답을 그대로 반환하므로 응답 전체 캐싱이 DB 부하 감소 효과가 가장 직접적이다.

### Redis 장애 시 API 실패

캐시 계층 문제를 빨리 드러낼 수는 있다.
하지만 Redis는 최적화 계층이고 MySQL이 source of truth이므로 API 가용성을 우선해 채택하지 않는다.
