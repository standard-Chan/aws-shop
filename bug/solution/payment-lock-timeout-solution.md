# Payment Lock Timeout 해결 과정

## 개요
- 문제 대상: `POST /api/payments`
- 재현 환경: MySQL InnoDB, `payment(order_id, status)` unique key
- 부하 조건: 같은 `orderId`, 같은 `status=NOT_STARTED`로 50개 동시 요청
- 관찰 오류: `Lock wait timeout exceeded; try restarting transaction`

## 1. 가설

### 가설 1
- 첫 번째 트랜잭션이 `(order_id, status)` unique key 위치에 대한 insert를 성공한 뒤에도
  commit이 늦어지면,
  뒤 요청들은 duplicate 판정 전에 해당 unique index entry에서 lock wait에 들어간다.
- 그 대기 시간이 길어지면 duplicate key가 아니라 `lock wait timeout`으로 종료된다.

### 가설 2
- 반대로 첫 번째 트랜잭션이 insert 직후 아주 빠르게 commit되면,
  뒤 요청들은 오래 기다리지 않고 duplicate 판정으로 빠르게 정리된다.
- 따라서 `lock wait timeout`의 핵심 원인 중 하나는 "첫 commit 지연"이다.

## 2. 검증

### 검증 목적
- 기존 `createPayment()`는 외부 주문 조회와 JPA flush/commit 타이밍이 섞여 있어
  "첫 commit 지연"만 따로 떼어 보기가 어렵다.
- 그래서 외부 호출 없이 단일 `INSERT`만 수행하는 진단 API를 추가해,
  commit 지연 시간만 바꿔가며 비교했다.

### 추가한 진단 코드
- 진단 서비스: [PaymentInsertDiagnosticService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentInsertDiagnosticService.java)
- 진단 API: [PaymentController.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/presentation/PaymentController.java)
- k6 스크립트: [payment-diagnostic-insert-only.js](/mnt/c/Users/정석찬/Desktop/project/aws-shop/k6/payment-diagnostic-insert-only.js)

### 진단 API 구조
- `JdbcTemplate` 단일 `INSERT`
- `@Transactional(REQUIRES_NEW)`
- `holdBeforeCommitMillis=0`
  - insert 후 즉시 메서드 종료
  - 가능한 가장 빠른 commit
- `holdBeforeCommitMillis>0`
  - 트랜잭션 안에서 `sleep`
  - commit을 의도적으로 지연

### 실행 명령

#### 실험 A: 느린 commit
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_ID=2 \
  -e VUS=50 \
  -e AMOUNT=100.0000 \
  -e HOLD_BEFORE_COMMIT_MILLIS=3000 \
  k6/payment-diagnostic-insert-only.js
```

#### 실험 B: 빠른 commit
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_ID=1 \
  -e VUS=50 \
  -e AMOUNT=100.0000 \
  -e HOLD_BEFORE_COMMIT_MILLIS=0 \
  k6/payment-diagnostic-insert-only.js
```

#### 실험 C: 빠른 commit 재검증
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_ID=4 \
  -e VUS=50 \
  -e AMOUNT=100.0000 \
  -e HOLD_BEFORE_COMMIT_MILLIS=0 \
  k6/payment-diagnostic-insert-only.js
```

## 3. 결과

### 실험 A: `holdBeforeCommitMillis=3000`
- 성공: `1`
- 충돌 `409`: `8`
- 서버 에러 `500`: `41`
- 평균 응답 시간: 약 `50.52s`

해석:
- 첫 트랜잭션이 commit을 3초 지연하자,
  나머지 요청 상당수가 duplicate로 정리되기 전에 lock wait queue에서 오래 묶였다.
- 그 결과 `409` 대신 `500 lock wait timeout`이 대량 발생했다.

### 실험 B: `holdBeforeCommitMillis=0`
- 성공: `1`
- 충돌 `409`: `49`
- 서버 에러 `500`: `0`
- 평균 응답 시간: 약 `61ms`

해석:
- 첫 트랜잭션이 insert 후 곧바로 commit되자,
  나머지 요청은 모두 빠르게 duplicate로 정리됐다.
- `lock wait timeout`은 발생하지 않았다.

### 실험 C: `holdBeforeCommitMillis=0` 재검증
- 성공: `1`
- 충돌 `409`: `49`
- 서버 에러 `500`: `0`
- 평균 응답 시간: 약 `60ms`

해석:
- 빠른 commit 결과가 우연이 아니라는 점을 다시 확인했다.

## 4. 결론

### 검증된 사실
- 이 진단 경로에서는 "첫 commit 지연"이 `lock wait timeout` 발생의 직접 원인이다.
- 첫 commit이 빠르면, 같은 unique key 경쟁이 `500 timeout`이 아니라 `409 duplicate`로 수렴한다.

### 주의점
- 이 결론은 `insert-only` 진단 경로에서 검증된 것이다.
- 실제 `createPayment()`는 JPA flush 시점, 외부 주문 조회, 스레드 스케줄링 등 추가 변수가 있다.
- 그래도 적어도 "첫 commit 지연이 timeout을 만든다"는 핵심 가설은 실험으로 입증됐다.

## 5. 해결한 방법

### 이번에 적용한 해결 방법
- 테스트와 원인 분리를 위해
  "외부 조회 없는 단일 insert + 매우 짧은 commit 경로"를 별도 진단 API로 만들었다.
- 즉 해결의 첫 단계는 "원인을 분리해서 증명할 수 있는 최소 경로"를 확보한 것이다.

### 적용 내용
- `JdbcTemplate` 단일 `INSERT`
- `REQUIRES_NEW` 짧은 트랜잭션
- 선택적 `holdBeforeCommitMillis` 주입
- k6로 빠른 commit / 느린 commit 비교

### 이 방법으로 확인한 것
- commit을 늦추면 timeout이 늘어난다.
- commit을 빠르게 끝내면 timeout이 사라지고 duplicate 응답으로 정리된다.

## 6. 다음 해결 방향

### 단기
- 기존 `createPayment()`에서도
  첫 write transaction이 가능한 한 짧게 끝나도록 구조를 줄인다.
- save 직전/직후에 불필요한 처리와 대기 시간을 넣지 않는다.

### 중기
- unique key에 충돌을 맡기지 말고,
  `orderId` 단위 선점 또는 기존 결제 재사용 흐름으로 바꾼다.
- 즉 "중복 생성 제어"를 DB 오류 처리에 맡기지 않고 애플리케이션 플로우로 끌어올린다.

### 장기
- `payment(order_id, status)` unique key는 최종 무결성 방어선으로 남기고,
  실제 동시성 제어는 상위 자원 선점으로 해결한다.

## 7. 요약
- 가설: 첫 commit 지연이 lock wait timeout의 원인이다.
- 검증: insert-only 진단 API와 k6로 commit 지연 시간을 비교했다.
- 결과: `holdBeforeCommitMillis=3000`에서는 `500` 다수, `0`에서는 `409`만 발생했다.
- 해결: 빠른 commit 경로를 분리 구현해 원인을 입증했고, 실제 해결 방향도 "첫 write transaction 단축 + 사전 선점"으로 정리됐다.
