# Product Ranking k6 Load Test

## 목적

`event-pipeline:product-ranking` 서버가 상품 조회와 장바구니 추가 이벤트를 초당 5천 건 문제없이 실시간 처리할 수 있는지 확인한다.
요청 payload는 `UserBehaviorEventMessage` 형태를 사용한다.

## 대상

- 서버: `event-pipeline:product-ranking`
- 기본 URL: `http://localhost:18083`
- API: `POST /api/event-pipeline/product-ranking/events`
- 이벤트 타입: `PRODUCT_VIEW`, `ADD_TO_CART`
- 기본 TPS: `5000`
- 기본 실행 시간: `1m`

## 실행

먼저 product-ranking 서버를 실행한다.

```bash
./gradlew :event-pipeline:product-ranking:bootRun
```

부하 테스트를 실행한다.

```bash
TPS=5000 \
DURATION=1m \
BASE_URL=http://localhost:18083 \
k6 run k6/product-ranking/product-ranking-events.js
```

랭킹 조회 부하 테스트를 실행한다.

```bash
TPS=1000 \
DURATION=1m \
WINDOW=ONE_HOUR \
LIMIT=10 \
BASE_URL=http://localhost:18083 \
k6 run k6/product-ranking/product-ranking-rankings.js
```

여러 window를 번갈아 조회할 수도 있다.

```bash
TPS=1000 \
DURATION=1m \
WINDOWS=ONE_HOUR,ONE_DAY,ONE_WEEK \
LIMIT=10 \
BASE_URL=http://localhost:18083 \
k6 run k6/product-ranking/product-ranking-rankings.js
```

## 환경 변수

- `BASE_URL`: 대상 서버 URL. 기본값 `http://localhost:18083`
- `TPS`: 초당 요청 수. 기본값 `5000`
- `DURATION`: 테스트 시간. 기본값 `1m`
- `PRE_ALLOCATED_VUS`: 사전 할당 VU 수. 기본값 `5000`
- `MAX_VUS`: 최대 VU 수. 기본값 `20000`
- `REQUEST_TIMEOUT`: 요청 timeout. 기본값 `3s`
- `USER_ID_MIN`: 시작 userId. 기본값 `1`
- `USER_ID_RANGE`: userId 분산 범위. 기본값 `100000`
- `PRODUCT_ID_MIN`: 시작 productId. 기본값 `1`
- `PRODUCT_ID_RANGE`: productId 분산 범위. 기본값 `100000`
- `EVENT_ID_MIN`: 시작 eventId. 기본값 `1`
- `KEYWORDS`: 상품 조회 이벤트 keyword 후보. 기본값 `macbook,keyboard,monitor,mouse,notebook`
- `WINDOW`: 랭킹 조회 window. `ONE_HOUR`, `ONE_DAY`, `ONE_WEEK` 중 하나. 기본값 `ONE_HOUR`
- `WINDOWS`: 여러 랭킹 조회 window를 쉼표로 지정한다. 지정하면 `WINDOW`보다 우선한다.
- `LIMIT`: 랭킹 조회 개수. 기본값 `10`

## 결과

결과 파일:

- `k6/results/product-ranking-events-summary.json`
- `k6/results/product-ranking-rankings-summary.json`

주요 지표:

- API 성공률: `202 Accepted` 응답 수 / 전체 요청 수
- API 실패률: `202`가 아닌 응답 수 / 전체 요청 수
- 이벤트 타입별 성공 수: `PRODUCT_VIEW`, `ADD_TO_CART`
- latency: `avg`, `p95`, `p99`, `max`

현재 threshold:

- 실패 이벤트 수 `0`
- 성공률 `100%`
- `p95 < 1000ms`
- `p99 < 3000ms`
- dropped iterations `0`
