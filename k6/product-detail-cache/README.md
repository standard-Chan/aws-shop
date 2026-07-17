# Product Detail Cache k6 Load Test

## 테스트 목적

`GET /api/products/{id}` 상품 상세 조회 API의 캐싱 효과를 검증하기 위한 k6 부하테스트다.

상품 상세 조회는 Product 본문뿐 아니라 feature, description, category, boughtTogether, image, video 데이터를 함께 읽는다. 캐시가 없거나 캐시 hit 비율이 낮으면 같은 API라도 요청 분포에 따라 DB 부하와 응답 시간이 크게 달라질 수 있다.

이 테스트는 실제 DB에 저장된 Product ID 목록을 입력으로 사용하고, 요청 분포를 3가지로 나눠 캐시 전략이 다음 상황에서 어떻게 동작하는지 비교한다.

- 전체 상품이 고르게 조회되는 상황
- 일부 인기 상품에 자연스럽게 트래픽이 몰리는 일반 상황
- 이벤트나 특가로 소수 상품에 트래픽이 강하게 몰리는 상황

## 파일 설명

| 파일 | 역할 |
| --- | --- |
| `export-product-detail-ids.sh` | 루트 `.env`의 MySQL 접속 정보를 읽고 `product.id` 전체를 추출한다. |
| `ids/product-ids.csv` | 부하테스트 입력 파일이다. 헤더 없이 Product ID가 한 줄에 하나씩 저장된다. |
| `ids/.gitkeep` | `ids` 디렉터리를 Git에 유지하기 위한 빈 파일이다. |
| `common.js` | CSV 로딩, Product 상세 조회 요청, metric, summary, 균등/Zipf 샘플링 공통 로직을 담는다. |
| `uniform-product-detail-cache.js` | 모든 제품 균등 요청 시나리오를 실행한다. |
| `zipf-product-detail-cache.js` | 일반적인 편향 분포 시나리오를 실행한다. Zipf `alpha=1.0`을 사용한다. |
| `event-product-detail-cache.js` | 이벤트/특가 상품 집중 시나리오를 실행한다. Zipf `alpha=1.1`을 사용한다. |

## ID 파일 생성

먼저 MySQL에 저장된 Product ID를 추출한다.

```bash
bash k6/product-detail-cache/export-product-detail-ids.sh
```

기본 출력 파일:

```text
k6/product-detail-cache/ids/product-ids.csv
```

스크립트는 다음 쿼리를 실행한다.

```sql
SELECT id
FROM product
ORDER BY id ASC;
```

`ids/product-ids.csv`는 대용량 로컬 입력 파일이므로 커밋하지 않는다.

## 실행 준비

Spring 서버를 먼저 실행한다.

```bash
./gradlew bootRun
```

k6가 설치되어 있어야 한다.

```bash
k6 version
```

smoke test는 낮은 TPS와 짧은 시간으로 먼저 실행한다.

```bash
TPS=1 DURATION=5s k6 run k6/product-detail-cache/uniform-product-detail-cache.js
```

## 실행 방법

### 1. 모든 제품 균등 요청 시나리오

전체 Product ID 중 하나를 균등 랜덤으로 선택해 요청한다. 캐시 hit이 낮은 상황이나 전체 상품 탐색성 트래픽을 가정한다.

```bash
TPS=1000 \
DURATION=5m \
BASE_URL=http://localhost:8080 \
k6 run k6/product-detail-cache/uniform-product-detail-cache.js
```

### 2. 일반적인 편향 분포 시나리오

Zipf `alpha=1.0` 분포로 요청 상품을 선택한다. 인기 상품에 요청이 몰리는 일반적인 커머스 조회 상황을 가정한다.

```bash
TPS=1000 \
DURATION=5m \
BASE_URL=http://localhost:8080 \
k6 run k6/product-detail-cache/zipf-product-detail-cache.js
```

### 3. 이벤트/특가 상품 집중 시나리오

Zipf `alpha=1.1` 분포로 요청 상품을 선택한다. 할인 이벤트나 특가 페이지 노출로 상위 소수 상품에 요청이 강하게 몰리는 상황을 가정한다.

```bash
TPS=1000 \
DURATION=5m \
BASE_URL=http://localhost:8080 \
k6 run k6/product-detail-cache/event-product-detail-cache.js
```

## 환경 변수

| 변수 | 설명 | 기본값 |
| --- | --- | --- |
| `BASE_URL` | 대상 Spring 서버 URL | `http://localhost:8080` |
| `TPS` | 초당 요청 수 | `100` |
| `DURATION` | 테스트 시간 | `1m` |
| `PRODUCT_IDS_FILE` | Product ID CSV 경로 | `./ids/product-ids.csv` |
| `SEED` | 요청 분포 재현용 seed | `20260717` |
| `REQUEST_TIMEOUT` | 요청 timeout | `10s` |
| `PRE_ALLOCATED_VUS` | 사전 할당 VU 수 | `max(TPS, 100)` |
| `MAX_VUS` | 최대 VU 수 | `max(TPS * 2, PRE_ALLOCATED_VUS)` |
| `P95_THRESHOLD_MS` | p95 latency threshold | `1000` |
| `P99_THRESHOLD_MS` | p99 latency threshold | `3000` |

다른 ID 파일을 사용하려면 `PRODUCT_IDS_FILE`을 지정한다.

```bash
PRODUCT_IDS_FILE=./ids/product-ids-small.csv \
TPS=100 \
DURATION=1m \
k6 run k6/product-detail-cache/uniform-product-detail-cache.js
```

## 결과 파일

요약 결과는 `k6/results`에 생성된다.

| 시나리오 | 결과 파일 |
| --- | --- |
| 균등 요청 | `k6/results/product-detail-cache-uniform-summary.json` |
| 일반 편향 | `k6/results/product-detail-cache-zipf-summary.json` |
| 이벤트 집중 | `k6/results/product-detail-cache-event-summary.json` |

summary에는 대상 URL, 시나리오 이름, Product ID 개수, seed, TPS, duration, 총 요청 수, 성공/실패 수, p95/p99 latency가 포함된다.

## 해석 기준

- `product_detail_success_200`: `200 OK` 상세 조회 성공 수
- `product_detail_not_found_404`: CSV에는 있었지만 API가 찾지 못한 상품 수
- `product_detail_failed_requests`: `200`이 아닌 전체 실패 수
- `product_detail_success_rate`: 전체 요청 중 `200 OK` 비율
- `http_req_duration`: API 응답 시간
- `dropped_iterations`: 목표 TPS를 맞추지 못해 누락된 iteration 수

캐싱 검증에서는 시나리오별 p95/p99 latency, DB 부하, 애플리케이션 cache hit 비율을 함께 비교한다.

## 주의사항

- Product ID는 JavaScript 정밀도 손상을 피하기 위해 숫자로 변환하지 않고 문자열로 사용한다.
- Zipf 시나리오는 실제 매출/조회수 데이터가 아니라 캐시 hot key 집중도를 만들기 위한 synthetic 분포다.
- `ids/product-ids.csv`는 `.gitignore` 대상이다.
- 높은 TPS로 실행하기 전 낮은 TPS smoke test로 서버, DB, ID 파일 경로를 먼저 확인한다.
