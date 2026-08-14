# PAYMENT

## 개요
- `payment` 도메인의 테스트 작성 전 참고 문서 모음이다.
- 현재 범위는 `PaymentController`, `PaymentService` 테스트 설계와 작성 계획이다.
- controller를 기본 축으로 두고, service는 보조 테스트로 분리한다.

## 문서 목록
- [../controller/payment-controller-api.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/controller/payment-controller-api.md)
- [PAYMENT_TEST_DESIGN.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/PAYMENT_TEST_DESIGN.md)
- [PAYMENT_TEST_PLAN.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/PAYMENT_TEST_PLAN.md)
- [problem/ORDER_PAYMENT_CREATION_GAP.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/problem/ORDER_PAYMENT_CREATION_GAP.md)
- [problem/OVER_ENGINEERING_CHECK.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/problem/OVER_ENGINEERING_CHECK.md)
- [diagram/PAYMENT_SEQUENCE_DIAGRAM.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/diagram/PAYMENT_SEQUENCE_DIAGRAM.md)
- [exception/EXCEPTION.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/exception/EXCEPTION.md)

## 현재 구현 메모
- `POST /api/payments`: `orders` row에 쓰기 락을 걸고 멱등키를 생성한 뒤 `Order.idempotencyKeys` 1:N 컬렉션에 추가하고 `Payment`를 저장한다.
- 동일한 `orderId`로 결제 생성 요청이 다시 들어오면 `PaymentOrderAlreadyExecutingException`을 던지고 HTTP 409를 반환한다.
- `POST /api/payments/confirm`: 요청의 `paymentKey`를 내부 `Payment`에 기록하고, `TossPaymentClient.confirm()` 결과를 그대로 반환한다.
- 현재 요청 DTO에는 Bean Validation이 없어 `orderId` 누락, 금액 불일치 같은 입력 검증은 아직 명시적으로 보장되지 않는다.

## `POST /api/payments/confirm`의 `paymentKey`
- `paymentKey`는 이 서버가 생성하는 내부 결제 ID나 주문 ID가 아니다.
- `Payment` 엔티티 주석 기준으로, Toss 측에서 발급한 결제 고유 키를 Frontend가 받아 `/api/payments/confirm` 요청에 포함해 전달하는 값이다.
- 서버는 `ConfirmPaymentRequest.paymentKey`를 받아 `Payment.start(paymentKey)`에서 공백/null 검증 후 `Payment.paymentKey`에 저장한다.
- 이후 `PaymentService.confirmPayment()`는 `new TossPaymentConfirmRequest(confirmRequest.paymentId(), confirmRequest.paymentKey(), confirmRequest.amount())`를 만들어 Toss Payments 승인 API(`/v1/payments/confirm`)로 보낸다.
- 이때 `TossPaymentConfirmRequest.orderId`에는 내부 `paymentId`가 들어간다. 코드 주석상 현재 시스템에서는 내부 `paymentId`를 Toss의 `orderId`로 사용한다.
- 따라서 `/api/payments/confirm` 호출자는 결제창 또는 Toss 결제 인증 완료 이후 받은 `paymentKey`를 그대로 전달해야 한다. 테스트용 임의 문자열은 mock 테스트나 로컬 예시에서는 가능하지만, 실제 Toss 승인 요청에서는 Toss가 발급한 유효한 `paymentKey`여야 한다.

## 이번 변경 전에 읽을 순서
- [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java)
- [OrderClient.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/infrastructure/order/OrderClient.java)
- [Payment.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/domain/Payment.java)
- [PaymentRepository.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/domain/PaymentRepository.java)
- [PaymentServiceTest.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/test/java/jeong/awsshop/payment/application/PaymentServiceTest.java)
- [payment-controller-api.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/api/payment-controller-api.md)

## 결제 생성 멱등성 기준
- `PaymentService.createPayment()`는 외부 order API가 아니라 로컬 `OrderRepository.findByIdForUpdate()`로 주문을 잠근다.
- 잠긴 주문에 이미 `idempotencyKeys`가 있거나 상태가 `EXECUTING`이면 같은 `orderId`에 대한 중복 생성으로 보고 409를 반환한다.
- 첫 생성 요청은 `Order.idempotencyKeys`에 키를 추가한다. `idempotency_key.order_id`는 unique가 아니며 한 주문이 여러 멱등키 row를 가질 수 있는 1:N 매핑이다.
- 기존의 진행 중 결제 재사용, 만료 결제 복구, 활성 결제 유실 복구 분기는 사용하지 않는다.
