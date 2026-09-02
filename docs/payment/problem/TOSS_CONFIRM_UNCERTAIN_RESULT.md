# Toss confirm 결과 불확실성 처리

## 문제

Toss Payments 결제 승인 API는 외부 시스템에서 실제 결제를 확정한다. 따라서 우리 서버가 `/v1/payments/confirm` 요청을 보낸 뒤 timeout, 연결 끊김, Toss 5xx, 응답 유실이 발생하면 결제 결과를 알 수 없다.

이때 내부 시스템이 예외를 곧바로 결제 실패로 처리하면 다음 불일치가 생길 수 있다.

```text
1. 우리 서버가 Toss confirm 요청을 보낸다.
2. Toss는 결제를 승인한다.
3. 응답이 우리 서버에 도착하기 전에 네트워크 오류가 발생한다.
4. 우리 서버는 실패로 판단해 Payment FAILED, Order PENDING, 재고 복구를 실행한다.
5. Toss에는 실제 승인 완료 결제가 남는다.
```

핵심 위험은 `돈은 결제됐지만 주문은 실패한 상태`다. 외부 결제 API 호출 실패는 항상 결제 실패가 아니라, 결제 결과를 확정하지 못한 상태일 수 있다.

## 결정

- Toss confirm POST 요청의 `Idempotency-Key`는 내부 `paymentId` 문자열로 사용한다.
- Toss 에러 응답은 HTTP 상태와 에러 코드 기준으로 `CONFIRMED_FAILURE`, `UNCERTAIN`, `ALREADY_PROCESSED`로 분류한다.
- `CONFIRMED_FAILURE`만 내부 실패 보상 로직을 실행한다.
- `UNCERTAIN`, `ALREADY_PROCESSED`는 즉시 실패 처리하지 않고 Toss 조회와 confirm 1회 재시도로 결과 확정을 시도한다.
- 조회와 재시도 모두 결과를 확정하지 못하면 `Payment.status=EXECUTING`을 유지하고 후속 복구 흐름에 맡긴다.

## 에러 분류

### CONFIRMED_FAILURE

요청 값, 인증, 결제 수단 거절처럼 실패가 명확한 오류다.

예시:

- `INVALID_REQUEST`
- `INVALID_API_KEY`
- `UNAUTHORIZED_KEY`
- `REJECT_CARD_PAYMENT`
- `REJECT_ACCOUNT_PAYMENT`
- `REJECT_CARD_COMPANY`
- 그 외 재시도/조회 필요로 분류하지 않은 4xx 오류

처리:

```text
Payment FAILED
Order PENDING
예약 재고 RESTORED
클라이언트에는 결제 승인 실패 응답
```

### UNCERTAIN

Toss 요청 결과를 확정할 수 없는 오류다.

예시:

- timeout
- 연결 실패
- 응답 파싱 실패 또는 응답 유실
- Toss 5xx
- `PROVIDER_ERROR`
- `IDEMPOTENT_REQUEST_PROCESSING`
- 결제 조회의 `NOT_FOUND_PAYMENT`

처리:

```text
1. paymentKey로 Toss 결제 상태를 조회한다.
2. 조회가 DONE이면 내부 성공 후처리를 실행한다.
3. 조회가 CANCELED, ABORTED, EXPIRED이면 내부 실패 보상을 실행한다.
4. 조회 결과가 없거나 미완료 상태이면 같은 Idempotency-Key로 confirm을 1회 재시도한다.
5. 재시도도 불확실하면 EXECUTING 상태를 유지한다.
```

### ALREADY_PROCESSED

Toss가 이미 처리된 결제라고 응답한 경우다.

처리:

```text
1. paymentKey로 Toss 결제 상태를 조회한다.
2. DONE이면 내부 성공 후처리를 실행한다.
3. 실패 종료 상태이면 내부 실패 보상을 실행한다.
4. 조회가 불확실하면 EXECUTING 상태를 유지한다.
```

## confirm 처리 흐름

```text
1. Payment 조회
2. Order 조회 및 승인 가능한 주문 상태 검증
3. Payment 만료 검증
4. Payment 종료 상태 검증
5. paymentKey 검증
6. DB CAS로 NOT_STARTED -> EXECUTING, paymentKey 저장
7. orderId, amount 검증
8. 주문 라인 재고 예약
9. Toss confirm 요청
   - Idempotency-Key = paymentId
10. Toss confirm 성공
   - Payment SUCCESS
   - Order COMPLETED
   - 예약 재고 COMPLETED
11. Toss confirm 확정 실패
   - Payment FAILED
   - Order PENDING
   - 예약 재고 RESTORED
12. Toss confirm 결과 불확실
   - Toss 조회와 confirm 1회 재시도
   - 끝까지 불확실하면 EXECUTING 유지
```

## 후속 복구 흐름

서버 재시작 복구 또는 이후 주기 복구는 오래 남은 `EXECUTING` 결제를 대상으로 한다.

```text
1. paymentKey가 없으면 조회할 수 없으므로 EXECUTING 유지
2. 예약 재고가 없으면 Toss 요청 전 중단으로 보고 FAILED 처리
3. 예약 재고가 있으면 paymentKey로 Toss 조회
4. DONE이면 성공 복구
5. CANCELED, ABORTED, EXPIRED이면 실패 복구
6. NOT_FOUND_PAYMENT 또는 미완료 상태이면 같은 Idempotency-Key로 confirm 1회 재시도
7. 재시도도 불확실하면 EXECUTING 유지
```

`EXECUTING` 유지는 실패를 숨기는 것이 아니라, 외부 결제 결과가 확정될 때까지 내부 상태를 섣불리 닫지 않기 위한 보류 상태다.

## 테스트 기준

- Toss confirm 요청에 `Idempotency-Key=paymentId`가 전달되어야 한다.
- 확정 실패 오류는 기존 실패 보상 로직을 실행해야 한다.
- 불확실 오류 후 조회 결과가 `DONE`이면 성공 후처리를 실행해야 한다.
- 불확실 오류 후 조회가 실패하면 `EXECUTING` 상태를 유지하고 실패 보상을 실행하지 않아야 한다.
- 조회 결과 결제 기록이 없으면 confirm을 같은 멱등키로 1회만 재시도해야 한다.
- 후속 복구에서도 조회 미완료 또는 `NOT_FOUND_PAYMENT`이면 confirm을 같은 멱등키로 1회만 재시도해야 한다.
- 재시도 결과도 불확실하면 `EXECUTING` 상태를 유지해야 한다.

## 제외 범위

- HTTP client timeout 값 설정은 별도 이슈에서 처리한다.
- 오래된 `EXECUTING` 결제를 주기적으로 회수하는 스케줄러는 별도 이슈에서 처리한다.
- 웹훅 기반 비동기 상태 동기화는 별도 이슈에서 처리한다.
