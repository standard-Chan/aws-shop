# Payment Create Duplicate Lock Wait Timeout

## 개요
- 대상 API: `POST /api/payments`
- 재현 스크립트: [k6/payment-create-duplicate-guard.js](/mnt/c/Users/정석찬/Desktop/project/aws-shop/k6/payment-create-duplicate-guard.js)
- 발생 시각 기준 로그: `2026-05-28 15:18:59`
- DB 전제: MySQL InnoDB
- 제약조건 전제: `payment(order_id, status)` unique key

## 증상
- 같은 `orderId`에 대해 `status=NOT_STARTED`인 `Payment`를 50개 동시 생성 요청하면 기대값은 `1건 200`, `49건 409`다.
- 실제로는 일부 요청이 `409 DuplicatePaymentException`이 아니라 `500`으로 실패한다.
- 애플리케이션 로그에는 아래 예외가 남는다.

```text
org.springframework.dao.PessimisticLockingFailureException
Lock wait timeout exceeded; try restarting transaction
```

## 재현 조건
- k6는 같은 라운드에서 동일 `orderId`를 50 VU가 동시에 `POST /api/payments`로 전송한다.
- 현재 구현은 [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java:44)에서
  `orderClient.getOrder()` 후 곧바로 `paymentRepository.save(payment)`를 호출한다.
- 중복 방지는 사전 조회나 명시적 락 없이 DB unique key에만 의존한다.

## 코드 경로
1. [PaymentController.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/presentation/PaymentController.java:28)가 요청을 받는다.
2. [PaymentService.createPayment()](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java:44)가 주문 조회 후 `Payment(status=NOT_STARTED)`를 만든다.
3. [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java:67)에서 `paymentRepository.save(payment)`를 호출한다.
4. 1개 요청은 insert 성공한다.
5. 나머지 요청들은 같은 `(order_id, status)` unique key 슬롯에 대해 InnoDB 레벨에서 충돌한다.

## 원인 분석
- 이 버그의 본질은 "중복 생성 방지"를 애플리케이션 레벨의 선점 없이, 곧바로 InnoDB unique key 경쟁으로 해결하려는 구조다.
- InnoDB는 unique index에 insert 할 때 중복 여부를 확인하기 위해 해당 인덱스 엔트리 주변에 락을 건다.
- 동일한 `(order_id, status)` 값을 50개가 동시에 넣으면, 첫 성공 트랜잭션이 unique index 엔트리에 대한 잠금을 잡고 나머지 트랜잭션은 그 잠금이 풀릴 때까지 대기한다.
- 대기 중인 요청은 즉시 "중복키"로 떨어지는 것이 아니라, 먼저 락을 얻어 중복 여부를 재검사할 기회를 기다린다.
- 이 대기 시간이 `innodb_lock_wait_timeout`을 넘으면 MySQL은 duplicate key가 아니라 `Lock wait timeout exceeded`를 반환한다.
- Spring/JPA는 이를 `DataIntegrityViolationException`이 아니라 `PessimisticLockingFailureException` 계열로 번역할 수 있다.
- 현재 [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java:76)는 `DataIntegrityViolationException`만 `DuplicatePaymentException`으로 변환한다.
- 따라서 unique key 충돌이더라도 락 대기 시간 초과로 번역된 요청은 `409`가 아니라 `500`으로 노출된다.

## 왜 순수 랜덤처럼 보이는가
- 랜덤처럼 보이지만, 실제로는 스레드 스케줄링과 커밋 타이밍의 경쟁 결과다.
- 각 요청은 insert 전에 `orderClient.getOrder()`를 먼저 호출한다.
- 따라서 50개 요청이 HTTP 레벨에서 동시에 들어와도 DB insert 시점은 완전히 동시에 맞지 않는다.
- 어떤 요청은 주문 조회가 빨리 끝나서 먼저 unique key 잠금을 잡고, 어떤 요청은 조금 늦게 들어가 대기열 뒤로 밀린다.
- 이 대기열 길이와 각 커밋 시점이 매 실행마다 달라지므로,
  어떤 실행에서는 초반 2건 이후부터 timeout이 나고,
  어떤 실행에서는 수십 건이 처리된 뒤 timeout이 난다.
- 즉 "랜덤 오류"가 아니라 "경합 시 insert 도착 순서와 락 해제 순서가 매번 달라지는 구조적 문제"다.

## 왜 unique key만으로는 기대한 409 보장이 안 되는가
- unique key는 "최종 무결성 보장"에는 적합하다.
- 하지만 "고경합 환경에서 모든 중복 요청을 빠르고 안정적으로 409로 응답"하는 용도로는 충분하지 않다.
- 지금 구조에서는 DB가 중복을 감지하기 전에 먼저 락 대기가 발생할 수 있고, 그 경우 에러 타입이 duplicate가 아니라 timeout으로 바뀐다.
- 따라서 현재 기대인 "`1건 성공 + 나머지 전부 409`"는 unique key 단독 전략만으로는 결정론적으로 보장되지 않는다.

## 현재 구현에서 확인된 직접 원인
- [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java:67)에서 동일 키에 대한 concurrent insert를 그대로 허용한다.
- [PaymentService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentService.java:76)는 `DataIntegrityViolationException`만 중복으로 처리한다.
- `PessimisticLockingFailureException` 혹은 그 하위의 lock wait timeout 예외는 중복 시나리오로 번역되지 않는다.

## 결론
- 원인은 MySQL InnoDB가 잘못 동작하는 것이 아니다.
- 원인은 `payment(order_id, status)` unique key를 "동시성 제어 수단"으로 직접 사용한 현재 `createPayment()` 설계다.
- InnoDB는 이 경우 일부 요청을 duplicate key가 아니라 lock wait timeout으로 종료시킬 수 있다.
- 그래서 k6 결과가 `409`와 `500` 사이에서 흔들린다.

## 대응 방향
- 1순위: DB insert 전에 order 단위 선점 로직을 둔다.
  - 예: order 상태를 먼저 `EXECUTING`으로 점유하거나, `orderId` 기준 별도 락 row를 잡고 그 안에서 payment 생성 여부를 판단한다.
- 2순위: `orderId`로 기존 `NOT_STARTED` 또는 진행 중 결제를 재조회해 기존 결제를 반환하는 흐름으로 바꾼다.
- 3순위: 최소 방어로 `PessimisticLockingFailureException`도 duplicate 생성 충돌로 번역해 `409`로 내린다.
  - 이건 증상 완화일 뿐이며, 경합 자체를 줄이지는 못한다.
- 4순위: `orderClient.getOrder()` 뒤에 바로 insert 하지 말고, "주문 선점 -> 기존 payment 조회/생성" 순서로 구조를 바꾼다.

## 권장 수정 방향
- 가장 안전한 방향은 "order 단위로 한 번만 결제 생성이 가능하도록 상위 자원에서 선점"하는 것이다.
- `payment` 테이블의 unique key는 최종 방어선으로 남기고,
  실질적인 중복 제어는 애플리케이션 플로우에서 처리해야 한다.
- 그래야 k6 기대값인 `1건 성공 + 나머지 동일 응답`을 안정적으로 맞출 수 있다.
