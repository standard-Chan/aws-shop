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
- [problem/PAYMENT_EXECUTING_BEFORE_TOSS_CONFIRM.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/problem/PAYMENT_EXECUTING_BEFORE_TOSS_CONFIRM.md)
- [problem/PAYMENT_CONFIRM_CONCURRENCY_CAS.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/problem/PAYMENT_CONFIRM_CONCURRENCY_CAS.md)
- [problem/OVER_ENGINEERING_CHECK.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/problem/OVER_ENGINEERING_CHECK.md)
- [diagram/PAYMENT_SEQUENCE_DIAGRAM.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/diagram/PAYMENT_SEQUENCE_DIAGRAM.md)
- [exception/EXCEPTION.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/payment/exception/EXCEPTION.md)

## 현재 구현 메모
- `POST /api/payments`: `orders` row에 쓰기 락을 걸고 멱등키를 생성한 뒤 `Order.idempotencyKeys` 1:N 컬렉션에 추가하고 `Payment`를 저장한다.
- 동일한 `orderId`로 결제 생성 요청이 다시 들어오면 기존 활성 결제(`NOT_STARTED`, `EXECUTING`)를 `FAILED`로 변경한 뒤 새 `Payment`를 생성한다.
- 같은 주문의 실패/성공 결제 이력을 보존하기 위해 `payment.order_id`는 unique가 아니다.
- `POST /api/payments/confirm`: 결제 만료 여부를 먼저 확인하고, 유효한 결제에 한해 DB 조건부 update로 `Payment.status=EXECUTING`과 `paymentKey` 저장을 선점한 뒤 `orderId`로 주문 라인을 조회해 모든 상품 재고를 예약하고 `TossPaymentClient.confirm()` 결과를 그대로 반환한다.
- confirm 진입 시점에 `expiresAt <= now`이면 해당 결제를 `EXPIRED`로 저장하고 주문을 `PENDING`으로 되돌린 뒤 `PaymentExpiredException`을 던진다. 이때 재고 예약, Toss 승인, 주문 완료 처리는 실행하지 않는다.
- 현재 요청 DTO에는 Bean Validation이 없어 `orderId` 누락, 금액 불일치 같은 입력 검증은 아직 명시적으로 보장되지 않는다.

## `POST /api/payments/confirm`의 `paymentKey`
- `paymentKey`는 이 서버가 생성하는 내부 결제 ID나 주문 ID가 아니다.
- `paymentKey`는 결제 승인 시도를 분류하는 키이며, 중복 사용을 허용하지 않는다.
- 같은 `paymentKey`가 다시 들어온 요청은 같은 승인 시도의 중복 요청으로 보아야 하므로, `paymentKey`는 결제 승인 멱등키 역할을 한다.
- 서버는 `ConfirmPaymentRequest.paymentKey`를 받아 공백/null 검증 후 DB 조건부 update로 `Payment.paymentKey`에 저장한다.
- 결제 만료 검사는 `Payment.start(paymentKey)`보다 먼저 실행한다. 따라서 만료된 confirm 요청은 `paymentKey`를 저장하지 않고 상태도 `EXECUTING`으로 바꾸지 않는다.
- 서버는 요청의 `productId`, `quantity`를 받지 않고, `orderId`로 조회한 주문 라인의 모든 `productId`, `quantity`를 구매 처리 대상으로 사용한다.
- 이후 `PaymentService.confirmPayment()`는 `new TossPaymentConfirmRequest(confirmRequest.paymentId(), confirmRequest.paymentKey(), confirmRequest.amount())`를 만들어 Toss Payments 승인 API(`/v1/payments/confirm`)로 보낸다.
- 이때 `TossPaymentConfirmRequest.orderId`에는 내부 `paymentId`가 들어간다. 코드 주석상 현재 시스템에서는 내부 `paymentId`를 Toss의 `orderId`로 사용한다.
- 실제 외부 결제 연동에서는 `/api/payments/confirm` 호출자가 결제창 또는 외부 결제 인증 완료 이후 받은 유효한 `paymentKey`를 그대로 전달해야 한다. 테스트용 임의 문자열은 mock 테스트나 로컬 예시에서만 사용한다.

## 이번 변경 전에 읽을 순서
- [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java)
- [OrderClient.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/infrastructure/order/OrderClient.java)
- [Payment.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/domain/Payment.java)
- [PaymentRepository.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/domain/PaymentRepository.java)
- [PaymentServiceTest.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/test/java/jeong/awsshop/payment/application/PaymentServiceTest.java)
- [payment-controller-api.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/api/payment-controller-api.md)

## 결제 생성 멱등성 기준
- `PaymentService.createPayment()`는 외부 order API가 아니라 로컬 `OrderRepository.findByIdForUpdate()`로 주문을 잠근다.
- 주문이 이미 `EXECUTING`이면 같은 `orderId`의 기존 활성 결제를 실패 처리하고 새 결제를 생성한다.
- 첫 생성 요청은 `Order.idempotencyKeys`에 키를 추가한다. `idempotency_key.order_id`는 unique가 아니며 한 주문이 여러 멱등키 row를 가질 수 있는 1:N 매핑이다.
- 기존의 진행 중 결제 재사용, 만료 결제 복구, 활성 결제 유실 복구 분기는 사용하지 않는다. 결제 이력 보존을 위해 새 결제 생성이 기본 정책이다.

## 운영 DB 반영
- 현재 프로젝트는 Flyway/Liquibase를 쓰지 않는다.
- `prod`는 `ddl-auto: validate`이므로 운영 DB에는 배포 전에 `payment.order_id` unique index 제거 DDL을 먼저 적용해야 한다.
- MySQL 기준 예시: `ALTER TABLE payment DROP INDEX uk_payment_order_id;`

## 재고 예약 영속화 기준
- 결제 승인 CAS 성공 후 주문 라인의 재고를 선차감할 때 `stock_reservation` row를 함께 저장한다.
- 예약 row 생성과 `stock.quantity` 차감은 `StockReservationService.reserve()`의 같은 DB 트랜잭션에서 처리한다.
- Toss confirm은 재고 예약 트랜잭션 밖에서 호출한다.
- Toss confirm 성공 시 예약 row는 `COMPLETED`로 닫고, 실패 시 `RESERVED` row만 재고 복구 후 `RESTORED`로 닫는다.
- 예약 복구는 `status=RESERVED` 조건부 update로 먼저 복구 실행권을 선점하고, 선점에 성공한 row만 재고를 증가시켜 병렬 복구의 중복 증가를 막는다.
- 서버 재시작 복구에서 `EXECUTING` 결제에 예약 row가 없으면 `CAS 성공 후 예약 전 종료`로 판단해 Toss 조회와 재고 복구 없이 결제를 `FAILED`, 주문을 `PENDING`으로 복구한다.
- 예약 row가 있는 `EXECUTING` 결제는 Toss 상태가 `DONE`이면 `COMPLETED`, 그 외 상태이면 `RESTORED` 기준으로 멱등 복구한다.
- `payment_id,status` 인덱스는 결제 성공 완료, 실패 복구, 재시작 복구에서 특정 결제의 `RESERVED` 예약만 빠르게 찾기 위한 조회 기준이다.

### `stock_reservation` 운영 DDL
```sql
CREATE TABLE stock_reservation (
    id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    restored_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_reservation_payment_product UNIQUE (payment_id, product_id),
    INDEX idx_stock_reservation_payment_status (payment_id, status)
);
```
