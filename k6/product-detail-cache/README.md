# Product Detail Cache k6 Load Test

## 목적

`GET /api/products/{id}` 상품 상세 조회 캐싱 효과를 확인하기 위한 부하테스트다.

실제 MySQL에 저장된 Product ID 목록을 CSV로 준비한 뒤, 요청 분포를 바꿔가며 Spring 서버의 상세 조회 API에 요청을 보낸다.

## 파일

- `export-product-detail-ids.sh`: 루트 `.env`를 읽어 `product.id` 전체를 추출하는 스크립트
- `ids/product-ids.csv`: 추출된 Product ID 입력 파일. 헤더 없이 ID만 한 줄에 하나씩 저장한다.
- `common.js`: CSV 로딩, 요청 전송, metric, summary, 랜덤/Zipf 샘플링 공통 로직
- `uniform-product-detail-cache.js`: 모든 제품 균등 요청 시나리오
- `zipf-product-detail-cache.js`: 일반적인 편향 분포 시나리오. Zipf `alpha=1.0`
- `event-product-detail-cache.js`: 이벤트/특가 상품 집중 시나리오. Zipf `alpha=1.1`

## ID 파일 생성

프로젝트 루트에서 실행한다.

```bash
bash k6/product-detail-cache/export-product-detail-ids.sh
```

기본 출력 경로는 다음과 같다.

```text
k6/product-detail-cache/ids/product-ids.csv
```

스크립트는 다음 쿼리를 실행한다.

```sql
SELECT id
FROM product
ORDER BY id ASC;
```

## 실행

Spring 서버를 먼저 실행한다.

```bash
./gradlew bootRun
```

모든 제품 균등 요청 시나리오:

```bash
TPS=1000 \
DURATION=5m \
BASE_URL=http://localhost:8080 \
k6 run k6/product-detail-cache/uniform-product-detail-cache.js
```

일반적인 편향 분포 시나리오:

```bash
TPS=1000 \
DURATION=5m \
BASE_URL=http://localhost:8080 \
k6 run k6/product-detail-cache/zipf-product-detail-cache.js
```

이벤트/특가 상품 집중 시나리오:

```bash
TPS=1000 \
DURATION=5m \
BASE_URL=http://localhost:8080 \
k6 run k6/product-detail-cache/event-product-detail-cache.js
```

## 환경 변수

- `BASE_URL`: 대상 Spring 서버 URL. 기본값 `http://localhost:8080`
- `TPS`: 초당 요청 수. 기본값 `100`
- `DURATION`: 테스트 시간. 기본값 `1m`
- `PRODUCT_IDS_FILE`: Product ID CSV 경로. 기본값 `./ids/product-ids.csv`
- `SEED`: 요청 분포 재현용 seed. 기본값 `20260717`
- `REQUEST_TIMEOUT`: 요청 timeout. 기본값 `10s`
- `PRE_ALLOCATED_VUS`: 사전 할당 VU 수. 기본값 `max(TPS, 100)`
- `MAX_VUS`: 최대 VU 수. 기본값 `max(TPS * 2, PRE_ALLOCATED_VUS)`
- `P95_THRESHOLD_MS`: p95 threshold. 기본값 `1000`
- `P99_THRESHOLD_MS`: p99 threshold. 기본값 `3000`

## 시나리오

### 모든 제품 균등 요청

CSV에 있는 전체 Product ID 중 하나를 균등 랜덤으로 선택한다.

### 일반적인 편향 분포

Zipf `alpha=1.0` 분포로 rank를 선택한다. 인기 상품에 요청이 몰리는 일반적인 조회 상황을 가정한다.

### 이벤트/특가 상품 집중

Zipf `alpha=1.1` 분포로 rank를 선택한다. 할인 이벤트처럼 상위 소수 상품에 요청이 더 강하게 몰리는 상황을 가정한다.

## 결과

요약 결과는 `k6/results`에 생성된다.

- `k6/results/product-detail-cache-uniform-summary.json`
- `k6/results/product-detail-cache-zipf-summary.json`
- `k6/results/product-detail-cache-event-summary.json`

## 주의사항

- `ids/product-ids.csv`는 대용량 로컬 입력 파일이므로 커밋하지 않는다.
- Product ID는 JavaScript 정밀도 손상을 피하기 위해 숫자로 변환하지 않고 문자열로 사용한다.
- Zipf 시나리오는 실제 매출/조회수 데이터가 아니라 캐시 hot key 집중도를 만들기 위한 synthetic 분포다.
