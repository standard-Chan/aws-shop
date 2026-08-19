# Toss 결제 성공 후 서버 장애 복구 문제

## 1. 문제

### 1.1 상황

현재 결제 승인 흐름은 외부 결제 API인 Toss Payments를 통해 실제 결제를 처리한 뒤, 우리 DB에 내부 결제 상태와 주문 상태를 저장한다.

```text
1. 우리 서버 -> Toss 결제 승인 요청
2. 사용자 결제 처리
3. Toss 결제 승인 성공
4. Toss -> HTTP 200 + DONE 응답
5. DB에 Payment SUCCESS 상태 저장
6. Order 완료 처리
```

어려운 점은 `Toss 결제 승인 성공`과 `DB에 결제 성공 상태 저장`을 하나의 트랜잭션으로 묶을 수 없다는 점이다. Toss 결제는 외부 시스템에서 이미 돈이 결제되는 작업이고, DB 상태 저장은 우리 서버의 로컬 트랜잭션이기 때문이다.

### 1.2 이로 인해 발생하는 문제

Toss 결제 승인까지 성공한 직후 서버 프로세스가 종료되면 다음 상태가 된다.

```text
1. 우리 서버 -> Toss 결제 승인 요청
2. 사용자 결제 처리
3. Toss 결제 승인 성공
4. Toss -> HTTP 200 + DONE 응답
5. 서버 프로세스 종료
6. DB에는 Payment SUCCESS 저장 안 됨
7. Order 완료 처리와 후속 배송 처리도 실행 안 됨
```

이 경우 사용자는 실제로 결제를 했지만, 우리 서비스는 결제가 완료되었다는 사실을 DB에 반영하지 못한다. 결과적으로 주문 완료, 배송 처리 같은 후속 로직이 실행되지 않아 사용자가 물품을 받지 못할 수 있다.

### 1.3 문제가 발생하는 코드 위치

결제 승인 로직에서 위험한 구간은 Toss 승인 응답을 받은 직후부터 내부 상태 저장이 끝나기 전까지다.

```java
public TossPaymentConfirmResponse confirmPayment(ConfirmPaymentRequest confirmRequest) {
    ...

    try {
        // 결제 로직 검증
        ...

        // 주문 상품 전체 재고 예약 처리
        ...

        // Toss 결제 요청
        TossPaymentConfirmResponse response = tossPaymentClient.confirm(
            new TossPaymentConfirmRequest(confirmRequest.paymentId(),
                confirmRequest.paymentKey(), confirmRequest.amount()));

        // 이 지점에서 서버가 종료되면 Toss 결제는 성공했지만 DB 상태는 완료되지 않는다.

        // 결제 승인 완료 처리
        payment.complete();

        // Order 완료 처리
        ...
        return response;
    } catch (PaymentException | StockException exception) {
        // 복구 로직
    } catch (Exception e) {
        // 예외 처리
    }
}
```

따라서 서버가 비정상 종료되더라도, 재시작 이후 Toss의 실제 결제 상태와 내부 DB 상태를 다시 맞출 수 있는 보완이 필요하다.

## 2. 목표

핵심 목표는 `Toss 결제 상태`와 `내부 DB 결제 상태`를 최종적으로 같은 의미의 상태로 맞추는 것이다.

두 상태를 원자적으로 저장할 수는 없으므로, 장애가 발생하지 않게 막는 것이 아니라 장애 이후 불일치 상태를 찾고 복구하는 방향을 선택한다.

## 3. 문제 구체화

### 3.1 원인

근본 원인은 `Toss 결제 성공`과 `DB 성공 상태 저장`이 원자적으로 처리될 수 없다는 점이다.

Toss 승인이 성공하면 외부 결제 상태는 이미 `DONE`이지만, 서버 장애가 발생하면 내부 DB의 `Payment`는 여전히 중간 상태 또는 이전 상태로 남을 수 있다. 따라서 시스템은 두 상태가 달라질 수 있음을 전제로 복구 경로를 가져야 한다.

### 3.2 문제가 있는 결제를 찾는 방법

모든 결제를 매번 Toss와 대조할 수는 없다. 그래서 문제가 될 가능성이 높은 결제만 좁혀야 한다.

이 프로젝트의 `Payment`에는 결제 시작과 종료 사이를 나타내는 `EXECUTING` 상태가 있다. 정상 흐름에서 `EXECUTING`은 오래 유지될 수 없는 상태다. 결제는 최종적으로 성공 또는 실패로 닫혀야 하기 때문이다.

따라서 서버 재시작 시점에 오래 남아 있는 `EXECUTING` 결제는, Toss 승인 요청 직전이나 승인 직후 서버 장애로 끊겼을 가능성이 있는 복구 대상으로 볼 수 있다.

### 3.3 현재 진행 중인 결제와 구분하는 방법

서버 재시작 이후 새로 생성된 `EXECUTING` 결제는 현재 정상 처리 중인 결제일 수 있다. 복구 로직이 이 결제를 건드리면 오히려 정상 결제 흐름을 방해할 수 있다.

그래서 복구 대상은 다음 조건으로 제한한다.

```text
대상:
  status = EXECUTING
  AND createdAt < applicationStartupTime

제외:
  status != EXECUTING
  OR createdAt >= applicationStartupTime
```

서버 시작 전에 이미 `EXECUTING`이었던 결제만 이전 실행에서 남은 미완료 결제로 판단한다.

## 4. 선택

### 4.1 DB 결제 상태를 기준으로 맞추기

DB 상태를 기준으로 맞춘다는 것은, 내부 DB에 성공 상태가 없으므로 Toss 결제를 취소하거나 환불해서 내부 상태에 맞추는 방식이다.

이 방식은 상태를 단순하게 맞출 수 있지만, 사용자는 이미 결제를 완료했는데 다시 결제를 진행해야 한다. 사용자가 원래 의도한 구매를 서버 장애 때문에 처음부터 다시 해야 하므로 사용자 경험이 나쁘다.

따라서 기본 선택지로 두지 않는다.

### 4.2 Toss 결제 상태를 기준으로 맞추기

Toss 결제 상태를 기준으로 맞춘다는 것은, Toss에서 이미 확인된 실제 결제 결과를 source of truth로 보고 내부 DB 상태를 따라가게 만드는 방식이다.

Toss 조회 결과가 `DONE`이면 결제 성공 이후 로직을 이어서 실행한다. 즉 `Payment`를 `SUCCESS`로 바꾸고, `Order`를 완료 처리한다. 사용자는 원래 의도한 결제를 그대로 완료하게 된다.

단점은 서버 장애와 재시작 사이의 시간만큼 주문 완료 후속 처리나 안내가 늦어질 수 있다는 점이다. 하지만 결제를 취소하고 사용자가 다시 결제하게 만드는 것보다 낫다.

따라서 복구 기준은 Toss 결제 상태로 결정한다.

## 5. 결정한 흐름

### 5.1 결제 승인 중간 상태 저장

Toss 승인 요청 전에 내부 DB에 `Payment.status=EXECUTING`과 `paymentKey`를 저장한다.

```text
1. Payment 조회
2. 주문 조회 및 주문 상태 검증
3. 결제 만료 검증
4. payment.start(paymentKey)
5. orderId, amount 검증
6. 주문 라인 재고 예약
7. Payment를 EXECUTING 상태로 저장
8. Toss Payments 승인 요청
9. 성공 시 Payment SUCCESS 저장 및 Order 완료 처리
10. 실패 시 FAILED 저장, 주문 PENDING 복구, 예약 재고 복구
```

이렇게 하면 서버가 Toss 승인 요청 직전이나 승인 직후 종료되어도, DB에 해당 결제가 결제 승인 구간에 들어갔다는 사실이 남는다.

### 5.2 서버 재시작 복구

복구는 서버 재시작 시 자동 실행한다.

1. `status=EXECUTING`이고 `createdAt < applicationStartupTime`인 결제를 조회한다.
2. 저장된 `paymentKey`로 Toss 결제 상태를 조회한다.
3. Toss 결제 상태와 내부 결제 상태가 다르면 Toss 상태를 기준으로 맞춘다.
4. Toss 상태가 `DONE`이면 기존 `confirmPayment()`의 Toss 승인 성공 이후 로직을 이어서 수행한다.
5. Toss 상태가 `DONE`이 아니면 기존 실패 보상 로직처럼 결제를 실패로 닫고 주문과 재고를 복구한다.

## 6. 복구 실패 처리

- `paymentKey`가 없으면 Toss 조회가 불가능하므로 상태를 변경하지 않고 로그만 남긴다.
- Toss 조회 자체가 실패하면 외부 상태를 확정할 수 없으므로 상태를 변경하지 않고 로그만 남긴다.
- 한 Payment 복구가 실패해도 다른 Payment 복구는 계속 진행한다.

## 7. 제외 범위

- 재고 예약 도중 서버가 종료되는 경우까지는 이번 변경으로 해결하지 않는다.
- 해당 시점에는 DB에 `NOT_STARTED`가 남을 수 있다.
- 재고 예약 중 장애까지 복구하려면 재고 예약 자체를 별도 상태 또는 보상 가능한 예약 모델로 설계해야 한다.
- Toss confirm 재시도와 `Idempotency-Key` 기반 중복 승인 방지는 별도 브랜치에서 다룬다.

## 8. 테스트 기준

- 재고 예약 후 Toss 승인 요청 전에 `paymentRepository.save(payment)`가 먼저 호출되어야 한다.
- 첫 저장 시점의 `Payment.status`는 `EXECUTING`, `paymentKey`는 요청 값이어야 한다.
- Toss 승인 실패 시에는 중간 `EXECUTING` 저장 이후 최종 `FAILED` 저장이 발생해야 한다.
- 재고 예약 실패 시에는 Toss 승인 요청이 호출되지 않고, 기존처럼 결제가 `FAILED`로 닫혀야 한다.
- 서버 시작 복구 구현 시 `createdAt < applicationStartupTime`인 `EXECUTING` Payment만 조회하는지 검증해야 한다.
- 서버 시작 이후 생성된 `EXECUTING` Payment는 복구 대상에서 제외되는지 검증해야 한다.
- Toss 상태가 `DONE`이면 내부 결제와 주문이 성공으로 복구되는지 검증해야 한다.
- Toss 상태가 `DONE`이 아니면 내부 결제 실패, 주문 `PENDING`, 주문 라인 전체 재고 복구가 수행되는지 검증해야 한다.
- 복구 중 한 건이 실패해도 다음 건 복구가 계속되는지 검증해야 한다.
