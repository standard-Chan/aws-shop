# Product 상세 캐시 비동기 저장 ADR

## Status

Accepted

## Context

현재 `GET /api/products/{id}`의 Redis MISS 경로는 DB에서 상세 응답을 조립한 뒤 Redis 저장까지 같은 요청 thread에서 수행한다.
이 구조에서는 DB 조회가 끝났더라도 Redis `SET`이 완료될 때까지 API 응답을 반환하지 못한다.

Redis 캐시는 source of truth가 아니라 반복 조회 성능을 위한 보조 계층이다.
MISS 요청의 사용자 응답은 DB 조회 결과로 이미 확정되므로, Redis 저장 완료를 응답 경로에서 기다릴 필요가 없다.

## Decision

Redis MISS 후 캐시 저장을 비동기 best-effort 작업으로 분리한다.

- 변경 전: `DB 조회 -> Redis 저장 완료 대기 -> 응답`
- 변경 후: `DB 조회 -> Redis 저장 비동기 제출 -> 응답`

비동기 실행은 Spring `@Async`와 상품 상세 캐시 전용 executor를 사용한다.
저장 실패는 기존 정책처럼 warn 로그만 남기고 API 응답에는 영향을 주지 않는다.

캐시 HIT 조회와 TTL 갱신은 기존처럼 동기 처리한다.
이번 문제는 MISS 후 저장 대기 시간이므로 저장 경로만 비동기화한다.

## Rationale

### MISS 응답 지연을 줄인다

MISS 요청은 DB 조회가 끝난 시점에 반환할 응답이 완성된다.
Redis 저장을 비동기화하면 Redis latency가 사용자 응답 시간에 직접 더해지지 않는다.

### 캐시 저장은 best-effort가 자연스럽다

Redis 저장 실패는 다음 요청에서 다시 MISS가 발생하게 만들 뿐, 현재 응답의 정합성을 깨지 않는다.
따라서 저장 성공을 요청 thread에서 보장하지 않는다.

### 전용 executor로 부하를 격리한다

공용 async executor에 의존하지 않고 `productDetailCacheTaskExecutor`를 둔다.
상품 상세 캐시 저장 지연이나 적체가 다른 비동기 작업에 영향을 주지 않게 한다.

## Consequences

### Positive

- Redis MISS 응답 시간이 Redis 저장 latency에 묶이지 않는다.
- Redis 저장 실패가 API 응답 실패로 전파되지 않는다.
- 캐시 저장 thread pool 크기와 queue를 별도 설정할 수 있다.

### Negative

- 응답 직후 짧은 시간 동안 아직 Redis에 값이 없을 수 있다.
- 같은 product id에 동시 MISS가 발생하면 중복 저장될 수 있다.
- executor queue가 가득 차면 캐시 저장 작업이 거부될 수 있다.

## Alternatives Considered

### 동기 저장 유지

구현은 단순하지만 현재 관측된 문제처럼 Redis 저장 latency가 API 응답 시간에 포함된다.
따라서 채택하지 않는다.

### ApplicationEvent 발행

요청 흐름과 저장 흐름을 이벤트로 분리할 수 있다.
하지만 현재 요구는 단순한 캐시 저장 비동기화이며, event type과 listener를 추가하는 구조는 과하다.
따라서 채택하지 않는다.

### 별도 write-behind queue

재시도, backpressure, 영속 queue까지 확장하기 좋다.
하지만 초기 문제 해결 범위에 비해 복잡도가 크고, 캐시 저장은 유실 가능 best-effort로 충분하다.
따라서 채택하지 않는다.
