# Toss 승인 호출 전 EXECUTING 저장 문제

## 문제 정의
- `confirmPayment`는 `Payment.start(paymentKey)`로 메모리상의 결제 상태를 `EXECUTING`으로 바꾼 뒤 재고 예약과 Toss Payments 승인 요청을 이어서 수행한다.
- 기존 흐름에서는 Toss 승인 요청이 끝난 뒤에야 `paymentRepository.save(payment)`가 호출된다.
- 따라서 재고 예약이 끝난 뒤 Toss 승인 요청 직전에 서버가 종료되면 DB에는 여전히 `NOT_STARTED`가 남을 수 있다.
- 이 경우 재시작 후에는 실제로 승인 요청 직전까지 진행된 결제인지, 아직 승인 처리를 시작하지 않은 결제인지 구분하기 어렵다.

## 목표
- Toss Payments 승인 요청을 호출하기 전에 내부 DB에 `Payment.status=EXECUTING`과 `paymentKey`를 명확히 저장한다.
- 서버 종료 후에도 해당 결제가 승인 처리 구간에 진입했다는 사실을 DB 상태만으로 확인할 수 있게 한다.
- 이번 범위는 재고 예약 완료 후 Toss 승인 요청 직전의 장애 보호로 한정한다.

## 선택한 흐름
```text
1. Payment 조회
2. 주문 조회 및 주문 상태 검증
3. 결제 만료 검증
4. payment.start(paymentKey)
5. orderId, amount 검증
6. 주문 라인 재고 예약
7. Payment를 EXECUTING 상태로 저장
8. Toss Payments 승인 요청
9. 성공 시 SUCCESS 저장 및 주문 완료 처리
10. 실패 시 FAILED 저장, 주문 PENDING 복구, 예약 재고 복구
```

## 결정
- `paymentRepository.save(payment)`를 재고 예약 이후, `tossPaymentClient.confirm(...)` 호출 이전에 실행한다.
- `PaymentStatus` enum, 요청/응답 DTO, DB 스키마는 변경하지 않는다.
- 잡힌 비즈니스 실패와 Toss 실패는 기존 정책대로 `FAILED`로 닫는다.
- JVM 종료처럼 catch되지 않는 장애만 `EXECUTING` 보존 대상으로 본다.

## 복구 실행 시점
- `EXECUTING` 상태로 남은 결제 복구는 서버 시작 시점에 실행한다.
- 애플리케이션 시작 시각을 `applicationStartupTime`으로 기록한다.
- 서버 시작 복구 로직은 `status=EXECUTING` 이면서 `createdAt < applicationStartupTime` 인 Payment 만 대상으로 삼는다.
- `createdAt >= applicationStartupTime` 인 `EXECUTING` Payment 는 현재 서버 실행 이후 시작된 정상 진행 중 결제로 판단하고 자동 복구 대상에서 제외한다.

## 복구 대상 기준
```text
대상:
  status = EXECUTING
  AND createdAt < applicationStartupTime

제외:
  status != EXECUTING
  OR createdAt >= applicationStartupTime
```

## 복구 시점 결정 이유
- 서버 종료 전 이미 `EXECUTING`으로 저장된 결제는 Toss 승인 요청 직전 또는 요청 중에 프로세스가 종료됐을 가능성이 있다.
- 서버 재시작 이후 새로 `EXECUTING`이 된 결제는 같은 프로세스에서 현재 처리 중일 수 있다.
- 따라서 서버 시작 시 복구가 현재 진행 중인 결제를 건드리지 않으려면 이전 실행에서 만들어진 `EXECUTING` 결제만 골라야 한다.
- 현재 모델에는 `executingStartedAt` 같은 별도 필드가 없으므로, 우선은 기존 `createdAt`을 서버 시작 시각과 비교해 이전 실행의 결제를 식별한다.

## 이후 복구 동작 설계에서 정할 내용
- Toss Payments 의 `paymentKey` 기반 결제 조회 API로 외부 결제 상태를 확인할지 정한다.
- Toss 상태가 `DONE`, `ABORTED`, `EXPIRED`, `CANCELED` 등일 때 내부 `PaymentStatus`와 Order 상태를 어떻게 맞출지 정한다.
- 복구 중 재고 예약이 이미 반영되었는지 확인하거나 보상할 수 있는 기준을 별도로 정한다.
- 복구 실패 시 재시도, 로그, 운영자 개입 대상 상태를 어떻게 남길지 정한다.

## 복구 동작 결정
- 복구 대상 Payment를 찾으면 저장된 `paymentKey`로 Toss Payments 결제 상태를 조회한다.
- 멱등키와 Toss confirm 재시도는 이번 범위에서 제외한다.
- Toss 조회 결과가 `DONE`이면 기존 `confirmPayment()`의 Toss 승인 성공 이후 로직을 이어서 수행한다.
  - `Payment`를 `SUCCESS`로 변경한다.
  - 주문을 `COMPLETED`로 변경한다.
  - 재고는 이미 예약된 상태로 보고 추가 복구하지 않는다.
- Toss 조회 결과가 `DONE`이 아니면 기존 `confirmPayment()`의 catch 보상 로직처럼 처리한다.
  - `Payment`를 `FAILED`로 변경한다.
  - 주문을 `PENDING`으로 변경한다.
  - 주문 라인 전체 재고를 복구한다.
- 서버가 종료됐다가 재시작되기까지 꽤 긴 시간이 지났을 수 있으므로, 이전 실행에서 남은 결제를 계속 진행 중이라고 신뢰하지 않는다.
- 따라서 `DONE`으로 확정된 결제만 성공으로 복구하고, 그 외 상태는 일관되게 실패로 닫는 쪽이 운영상 더 안전하다고 판단한다.

## 복구 실패 처리
- `paymentKey`가 없으면 Toss 조회가 불가능하므로 상태를 변경하지 않고 로그만 남긴다.
- Toss 조회 자체가 실패하면 외부 상태를 확정할 수 없으므로 상태를 변경하지 않고 로그만 남긴다.
- 한 Payment 복구가 실패해도 다른 Payment 복구는 계속 진행한다.

## 제외 범위
- 재고 예약 도중 서버가 종료되는 경우까지는 이번 변경으로 해결하지 않는다.
- 해당 시점에는 DB에 `NOT_STARTED`가 남을 수 있다.
- 재고 예약 중 장애까지 복구하려면 재고 예약 자체를 별도 상태 또는 보상 가능한 예약 모델로 설계해야 한다.
- Toss confirm 재시도와 `Idempotency-Key` 기반 중복 승인 방지는 별도 브랜치에서 다룬다.

## 테스트 기준
- 재고 예약 후 Toss 승인 요청 전에 `paymentRepository.save(payment)`가 먼저 호출되어야 한다.
- 첫 저장 시점의 `Payment.status`는 `EXECUTING`, `paymentKey`는 요청 값이어야 한다.
- Toss 승인 실패 시에는 중간 `EXECUTING` 저장 이후 최종 `FAILED` 저장이 발생해야 한다.
- 재고 예약 실패 시에는 Toss 승인 요청이 호출되지 않고, 기존처럼 결제가 `FAILED`로 닫혀야 한다.
- 서버 시작 복구 구현 시 `createdAt < applicationStartupTime` 인 `EXECUTING` Payment 만 조회하는지 검증해야 한다.
- 서버 시작 이후 생성된 `EXECUTING` Payment 는 복구 대상에서 제외되는지 검증해야 한다.
- Toss 상태가 `DONE`이면 내부 결제와 주문이 성공으로 복구되는지 검증해야 한다.
- Toss 상태가 `DONE`이 아니면 내부 결제 실패, 주문 `PENDING`, 주문 라인 전체 재고 복구가 수행되는지 검증해야 한다.
- 복구 중 한 건이 실패해도 다음 건 복구가 계속되는지 검증해야 한다.
