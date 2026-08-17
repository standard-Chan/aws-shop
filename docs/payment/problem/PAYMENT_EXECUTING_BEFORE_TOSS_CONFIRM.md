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

## 제외 범위
- 재고 예약 도중 서버가 종료되는 경우까지는 이번 변경으로 해결하지 않는다.
- 해당 시점에는 DB에 `NOT_STARTED`가 남을 수 있다.
- 재고 예약 중 장애까지 복구하려면 재고 예약 자체를 별도 상태 또는 보상 가능한 예약 모델로 설계해야 한다.

## 테스트 기준
- 재고 예약 후 Toss 승인 요청 전에 `paymentRepository.save(payment)`가 먼저 호출되어야 한다.
- 첫 저장 시점의 `Payment.status`는 `EXECUTING`, `paymentKey`는 요청 값이어야 한다.
- Toss 승인 실패 시에는 중간 `EXECUTING` 저장 이후 최종 `FAILED` 저장이 발생해야 한다.
- 재고 예약 실패 시에는 Toss 승인 요청이 호출되지 않고, 기존처럼 결제가 `FAILED`로 닫혀야 한다.
