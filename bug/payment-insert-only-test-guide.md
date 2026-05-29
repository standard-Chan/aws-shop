# Payment insert-only 테스트 가이드

## 목적
- `POST /api/payments/diagnostics/insert-only` 경로로
- 외부 주문 조회 없이 단일 `INSERT`만 수행했을 때
- `holdBeforeCommitMillis` 값에 따라 `409`와 `500 lock wait timeout` 비율이 어떻게 달라지는지 본다.

## 추가된 진단 API
- 경로: `POST /api/payments/diagnostics/insert-only`
- 구현: [PaymentInsertDiagnosticService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentInsertDiagnosticService.java)
- 특징:
  - `JdbcTemplate` 단일 `INSERT`
  - `@Transactional(REQUIRES_NEW)`
  - `holdBeforeCommitMillis=0`이면 insert 후 즉시 메서드 종료
  - `holdBeforeCommitMillis>0`이면 트랜잭션 안에서 sleep 후 commit

## 요청 바디

```json
{
  "orderId": 888001,
  "amount": 100.0000,
  "holdBeforeCommitMillis": 0
}
```

## 빠른 수동 확인

### 1. 기존 데이터 정리
```sql
DELETE FROM payment WHERE order_id = 888001;
```

### 2. 단건 요청
```bash
curl -X POST \
  'http://localhost:8080/api/payments/diagnostics/insert-only' \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId": 888001,
    "amount": 100.0000,
    "holdBeforeCommitMillis": 0
  }'
```

## k6 비교 실험

### 실험 A: 빠른 commit
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_ID=888001 \
  -e VUS=50 \
  -e AMOUNT=100.0000 \
  -e HOLD_BEFORE_COMMIT_MILLIS=0 \
  k6/payment-diagnostic-insert-only.js
```

### 실험 B: commit을 일부러 늦춤
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_ID=888002 \
  -e VUS=50 \
  -e AMOUNT=100.0000 \
  -e HOLD_BEFORE_COMMIT_MILLIS=3000 \
  k6/payment-diagnostic-insert-only.js
```

### 실험 C: 더 강하게 지연
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_ID=888003 \
  -e VUS=50 \
  -e AMOUNT=100.0000 \
  -e HOLD_BEFORE_COMMIT_MILLIS=10000 \
  k6/payment-diagnostic-insert-only.js
```

## 기대 해석
- `HOLD_BEFORE_COMMIT_MILLIS=0`
  - 첫 요청이 가장 빨리 커밋된다.
  - `500 lock wait timeout`이 줄고, 대부분 `409 duplicate`로 정리될 가능성이 높다.
- `HOLD_BEFORE_COMMIT_MILLIS=3000`
  - 첫 트랜잭션이 unique key 슬롯을 더 오래 쥔다.
  - 뒤 요청들이 더 오래 대기한다.
  - timeout 확률이 올라갈 수 있다.
- `HOLD_BEFORE_COMMIT_MILLIS=10000`
  - `innodb_lock_wait_timeout`보다 길면 `500`이 훨씬 잘 재현된다.

## 함께 보면 좋은 것

### 앱 로그
- `insert-only 결제 생성 시작`
- `커밋 전 트랜잭션 대기`
- `insert-only 중복 생성`

### MySQL 락 관측
```sql
SELECT
    w.REQUESTING_ENGINE_TRANSACTION_ID AS waiting_trx_id,
    w.BLOCKING_ENGINE_TRANSACTION_ID AS blocking_trx_id,
    rl.OBJECT_NAME,
    rl.INDEX_NAME,
    rl.LOCK_TYPE,
    rl.LOCK_MODE,
    rl.LOCK_DATA
FROM performance_schema.data_lock_waits w
JOIN performance_schema.data_locks rl
  ON w.REQUESTING_ENGINE_LOCK_ID = rl.ENGINE_LOCK_ID
WHERE rl.OBJECT_NAME = 'payment';
```

## 정리 포인트
- 이 실험으로 확인하려는 것은 "`첫 commit이 빨라지면 timeout이 줄어드는가`"다.
- 결과가 좋아져도 근본 해결은 아니다.
- 근본 원인은 여전히 같은 `(order_id, status)`로 여러 요청이 동시에 insert 경쟁에 들어가는 구조다.
