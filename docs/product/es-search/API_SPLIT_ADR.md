# MySQL LIKE 검색과 Elasticsearch 검색 API 분리 ADR

## Status

Accepted

## Context

현재 상품 keyword 검색은 `GET /api/products/keyword`에서 MySQL `LIKE %keyword%` 쿼리로 처리한다.
이 방식은 구현이 단순하고 기존 cursor pagination과 잘 맞지만, 대량 상품 데이터에서 keyword 검색 부하가 커질수록 DB에 부담을 준다.

초기 논의에서는 기존 keyword 검색 로직을 interface로 추상화하고 MySQL LIKE 구현과 ES 구현을 교체 가능하게 두는 방안을 검토했다.
그러나 ES 검색은 다음과 같은 고유 기능과 계약을 갖는다.

- `_score`
- highlight
- analyzer 기반 token 검색
- `search_after` 기반 pagination
- 향후 boost, suggest, autocomplete, synonym 등 검색 전용 기능

이 기능들은 MySQL LIKE 검색 모델과 자연스럽게 맞지 않는다.
공통 interface를 강하게 두면 ES 기능이 늘어날 때 MySQL 구현이 지원하기 어려운 계약을 강제하거나, ES 기능을 공통 계약 밖으로 계속 우회해야 한다.

## Decision

MySQL LIKE 검색과 Elasticsearch 검색은 API 단에서 분리한다.

- 기존 API: `GET /api/products/keyword`
- 신규 ES API: `GET /api/products/search`

공통 keyword search interface는 만들지 않는다.
두 API는 controller, service, response DTO, cursor 정책을 각각 가진다.

기존 `/keyword` API는 MySQL LIKE 기반 검색으로 유지한다.
신규 `/search` API는 ES 전용 검색으로 구현한다.

ES API는 기존 `ProductCategoryCursorResponse`를 재사용하지 않는다.
ES 전용 응답 DTO를 만들고, 초기부터 `score`, `highlight`, opaque `nextCursor`를 포함한다.

ES API 장애 시 서버 내부에서 MySQL LIKE fallback을 하지 않는다.
장애는 ES 검색 API의 실패로 드러내며, 클라이언트 또는 운영자가 필요하면 기존 `/keyword` API를 별도 호출할 수 있다.

## Rationale

### ES 기능 확장을 막지 않는다

ES 검색은 단순 DB 조회가 아니라 검색 엔진 기능을 활용하는 별도 read model이다.
API를 분리하면 score, highlight, analyzer, suggest 같은 기능을 MySQL LIKE 구현과 맞추려고 억지 추상화를 만들 필요가 없다.

### 기존 API 회귀 위험을 줄인다

기존 `/api/products/keyword` 계약을 그대로 유지하므로 기존 클라이언트와 테스트가 즉시 영향을 받지 않는다.
ES API는 신규 endpoint로 추가되어 비교, 실험, 부하테스트를 독립적으로 수행할 수 있다.

### 실패 정책이 명확하다

공통 interface와 fallback을 섞으면 `/search` 응답이 어떤 저장소에서 왔는지 불명확해질 수 있다.
API를 분리하면 `/keyword`는 MySQL LIKE, `/search`는 ES라는 책임이 명확하다.

### cursor 모델을 분리할 수 있다

기존 API는 `cursorId` 기반 cursor를 사용한다.
ES는 `search_after`에 정렬값 배열이 필요하므로, ES 전용 opaque cursor token이 더 자연스럽다.
API를 분리하면 기존 cursor 계약을 깨지 않고 ES에 맞는 pagination을 설계할 수 있다.

## Consequences

### Positive

- ES 검색 기능을 독립적으로 확장할 수 있다.
- 기존 MySQL LIKE API의 안정성을 유지한다.
- 부하테스트에서 MySQL API와 ES API를 직접 비교할 수 있다.
- ES 장애와 MySQL LIKE 검색의 동작이 섞이지 않는다.

### Negative

- 상품 검색 API가 두 개가 된다.
- 클라이언트가 `/keyword`와 `/search`의 차이를 알아야 한다.
- 일부 요청 parameter가 비슷하지만 응답 DTO와 cursor 계약이 달라진다.
- ES API용 테스트와 문서를 별도로 유지해야 한다.

## Alternatives Considered

### 공통 interface로 MySQL LIKE와 ES 구현 교체

초기에는 기존 keyword 검색 로직을 추출해 MySQL LIKE 구현과 ES 구현을 같은 interface 아래에 두는 방안을 고려했다.
입력값은 `keyword`, `size`, `sort`, `order`로 비슷하므로 첫 구현은 가능하다.

하지만 ES 응답에는 score, highlight, search_after cursor가 들어가고, 이후 검색 기능이 늘어날 가능성이 높다.
공통 interface는 시간이 갈수록 ES 기능을 제한하거나 MySQL 구현에 맞지 않는 메서드를 강제할 가능성이 있다.

따라서 채택하지 않는다.

### ES API 내부에서 MySQL LIKE fallback

ES 장애 시 기존 MySQL LIKE 검색으로 fallback하는 방안도 검토했다.
이 방식은 가용성에는 유리하지만, `/search` 응답이 ES 결과인지 MySQL 결과인지 모호해진다.
또한 score와 highlight 같은 ES 전용 필드를 안정적으로 제공하기 어렵다.

따라서 초기 ES 전용 API에서는 fallback을 두지 않는다.

### 기존 `/keyword` API를 ES로 교체

기존 endpoint를 유지한 채 내부 구현만 ES로 바꾸는 방식은 클라이언트 변경이 적다.
그러나 검색 결과 의미, cursor 정책, 장애 정책이 달라질 수 있어 회귀 위험이 크다.

따라서 기존 API는 유지하고 신규 API를 추가한다.

## Implementation Notes

- `/api/products/search`는 ES 전용 request/response DTO를 사용한다.
- Long id는 기존 product 문서 원칙에 따라 문자열로 응답한다.
- ES cursor는 opaque string으로 두고 내부 포맷을 public contract로 만들지 않는다.
- MySQL은 source of truth로 유지하고 ES는 재색인을 통해 생성되는 검색 read model로 본다.
- 전체 재색인은 관리 API로 시작한다.

### 재색인 조회 쿼리에서 product page를 서브쿼리로 먼저 확정하는 이유

`findProductSearchReindexPage`는 `product`를 `id ASC` cursor 기준으로 page 단위 조회한 뒤, 그 결과에 대표 이미지를 붙인다.
이때 `FROM` 절의 서브쿼리에서 product page를 먼저 확정하고 바깥 쿼리에서 `product_images`를 붙이는 형태를 사용한다.

의도는 재색인의 page 경계를 상품 기준으로 안정적으로 유지하는 것이다.
대표 이미지는 상품당 최대 1개만 선택하지만, 이미지 테이블은 1:N 관계다.
이미지 조인을 먼저 풀어놓고 전체 쿼리의 `WHERE`, `ORDER BY`, `LIMIT`로 page를 자르면, 이후 쿼리 수정 과정에서 join row 기준으로 limit이 적용되거나 상품 중복 row가 page 경계에 영향을 주는 실수를 만들기 쉽다.
반대로 product 서브쿼리에서 `WHERE (:cursorId IS NULL OR p.id > :cursorId)`, `ORDER BY p.id ASC`, `LIMIT :limit`을 먼저 적용하면, 재색인 batch의 기준이 항상 product row로 고정된다.

따라서 이 쿼리에서 서브쿼리는 성능 튜닝 목적이라기보다 책임 분리를 위한 구조다.
안쪽 쿼리는 "이번 batch에 포함할 상품 id 집합"을 결정하고, 바깥 쿼리는 "각 상품에 응답/색인용 대표 이미지를 보강"한다.
단순히 조건을 바깥 `WHERE`로 옮기는 것도 현재 형태와 같은 결과를 만들 수는 있지만, page 확정과 대표 이미지 보강의 경계가 흐려지므로 재색인 쿼리에서는 현재 구조를 유지한다.
