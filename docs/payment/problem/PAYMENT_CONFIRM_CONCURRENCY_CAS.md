# Payment confirm 동시 승인 CAS 처리

## 문제

같은 `paymentId`에 대해 `/api/payments/confirm` 요청이 동시에 들어오면 두 요청이 모두 같은 `Payment`를 `NOT_STARTED` 상태로 조회할 수 있다.

기존 흐름에서는 `payment.start(paymentKey)`가 Java 객체 상태만 먼저 바꾸고, 이후 `paymentRepository.save(payment)`가 실행되기 전까지 DB row 선점이 보장되지 않았다. 따라서 경합 요청이 동시에 재고를 차감하거나 Toss 승인 요청을 보낼 수 있는 위험이 있었다.

```text
T1: Payment(id=1, status=NOT_STARTED) 조회
T2: Payment(id=1, status=NOT_STARTED) 조회
T1: payment.start(paymentKey)
T2: payment.start(paymentKey)
T1/T2: 재고 차감 및 Toss confirm 진입 가능
```

## 결정

결제 승인 시작권은 DB 조건부 update로 선점한다.

```sql
UPDATE payment
SET status = 'EXECUTING',
    payment_key = :paymentKey
WHERE id = :paymentId
  AND status = 'NOT_STARTED'
```

반환 row count가 `1`이면 해당 요청이 승인 실행권을 얻은 것이다. 반환 row count가 `0`이면 이미 다른 요청이 결제를 시작했거나 상태가 바뀐 것이므로 `PaymentAlreadyExecutingException`을 던지고 `409 Conflict`로 응답한다.

## 변경 후 흐름

```text
1. Payment 조회
2. Order 조회 및 confirm 가능한 주문 상태 검증
3. Payment 만료 검증
4. Payment 종료 상태 검증
5. paymentKey null/blank 검증
6. DB CAS: NOT_STARTED -> EXECUTING, paymentKey 저장
7. CAS 실패 시 409 반환
8. CAS 성공 시 로컬 Payment 객체도 EXECUTING으로 동기화
9. try 블록 진입
10. orderId, amount 검증
11. 주문 라인 재고 예약
12. Toss confirm
13. 성공 시 Payment SUCCESS, Order COMPLETED
14. 실패 시 Payment FAILED, Order PENDING, 예약 재고 복구
```

`payment.start()`를 `try` 밖으로 옮긴 이유는 승인 처리 중 실패가 아니라 승인 시작 전 실행권 선점 단계이기 때문이다. CAS 실패 요청은 실제 승인 흐름에 들어가지 못했으므로 결제를 `FAILED`로 닫거나 주문을 `PENDING`으로 되돌리거나 재고를 복구하지 않는다.

## 제외 범위

- `validateOrderId`, `validateConfirmAmount`를 `try` 밖으로 옮기는 작업은 다음 단계에서 별도로 진행한다.
- Toss `Idempotency-Key` 헤더 연동은 이번 변경에 포함하지 않는다.
- `paymentKey` 전역 unique 제약 추가는 이번 변경에 포함하지 않는다.
- 재고 예약 중 서버 장애 복구는 기존 복구 문서의 제외 범위를 유지한다.

## 검증 기준

- 동시에 같은 `paymentId`로 confirm 요청을 보내도 CAS 성공 요청은 1건이어야 한다.
- CAS 실패 요청은 `409 Conflict`를 반환해야 한다.
- CAS 실패 요청은 재고 차감, Toss confirm, 주문 pending, Payment failed 저장을 실행하지 않아야 한다.
- CAS 성공 후 재고 예약 또는 Toss confirm 실패는 기존처럼 실패 보상 로직을 실행해야 한다.

## 기존 테스트만으로는 부족했던 이유

기존 단위 테스트와 repository 동시성 테스트는 CAS 구현의 핵심 규칙을 빠르게 검증하기에는 충분하다. 하지만 운영에서 문제가 발생하는 지점은 단일 메서드 호출이 아니라 실제 HTTP 요청이 동시에 들어오면서 Spring MVC, JSON 역직렬화, service 로직, repository update, DB connection pool이 함께 동작하는 경로다.

따라서 다음 한계가 남아 있었다.

- `PaymentServiceTest`는 CAS 실패 시 재고 차감, Toss confirm, 주문 상태 변경, 실패 보상이 실행되지 않는지 검증하지만, 실제 `/api/payments/confirm` 요청이 controller와 예외 핸들러를 거쳐 `409 Conflict`로 내려가는지는 검증하지 못한다.
- `PaymentRepositoryTest`는 같은 row에 대한 조건부 update가 1건만 성공하는지 검증하지만, 성공 요청 하나만 재고 예약, Toss confirm, 주문 완료까지 이어지는지는 검증하지 못한다.
- 단위 테스트는 순서를 통제한 mock 호출이므로 실제 HTTP worker thread, DB 커넥션 획득, transaction 경계, 응답 직렬화까지 포함한 병렬 경합을 재현하지 못한다.

그래서 HTTP 병렬 검증은 단위 테스트의 대체가 아니라, CAS 설계가 실제 애플리케이션 진입점에서도 같은 결과를 내는지 확인하는 통합 검증이다.

## HTTP 검증 중 발견한 추가 운영 이슈

초기 HTTP 병렬 검증에서는 `VUS=50` 요청이 모두 `500 Internal Server Error`로 실패했다. 실패 원인은 CAS update가 아니라 `PaymentService`가 주문 조회를 위해 같은 애플리케이션의 `/api/orders/{id}`를 HTTP로 다시 호출하는 구조와 `spring.jpa.open-in-view=true` 기본값이 결합된 커넥션 풀 고갈이었다.

외부 confirm 요청은 먼저 Payment를 조회한다. Open Session In View가 켜져 있으면 이 요청 범위의 EntityManager가 view rendering까지 유지될 수 있다. 이 상태에서 confirm 요청 50개가 다시 내부 Order HTTP 요청 50개를 만들면, 외부 요청과 내부 요청이 같은 Hikari pool을 경쟁하게 된다. 로컬 설정의 pool size가 30이면 내부 order 조회가 커넥션을 얻지 못해 timeout이 발생한다.

따라서 운영 경로에서는 `spring.jpa.open-in-view=false`로 설정한다. 서비스 계층의 `@Transactional` 경계 안에서 필요한 DTO를 완성하고, 웹 요청 범위가 DB 리소스를 오래 붙잡지 않도록 하기 위함이다.

## 병렬 HTTP 검증 설계

결제 승인 동시성 검증은 실제 Toss API 대신 mock Toss gateway를 사용한다. 외부 PSP의 네트워크 지연, 응답 제한, 중복 승인 정책이 섞이면 이번 검증의 관심사인 "우리 DB CAS가 승인 실행권을 1건만 선점하는가"를 분리해서 보기 어렵기 때문이다.

프로필은 기능명인 `payment-confirm-concurrency`를 새로 만들지 않고 기존 실행 환경 기준을 따른다.

- `dev`: `app.payment.toss.mode=mock`
- `prod`: `app.payment.toss.mode=real`
- `test`: `app.payment.toss.mode=mock`

이렇게 둔 이유는 실행 환경을 보면 Toss 연동 방식이 바로 드러나고, 나중에 `payment-confirm-concurrency` 같은 임시 검증명 프로필이 남아 의미를 헷갈리게 만들지 않기 위해서다.

dev/mock 모드에서만 다음 검증 API가 열린다.

```text
POST /test/payment-confirm-concurrency/fixtures
GET  /test/payment-confirm-concurrency/toss-stats
GET  /test/payment-confirm-concurrency/fixtures/{paymentId}/result
```

fixture API는 매 실행마다 상품 2개, 재고, `EXECUTING` 주문, `NOT_STARTED` 결제를 만든다. 이후 k6는 모든 VU가 같은 `paymentId`, `paymentKey`로 `/api/payments/confirm`을 동시에 호출한다.

기대 결과는 다음과 같다.

- `200 OK`: 1건
- `409 Conflict`: `VU - 1`건
- mock Toss confirm 호출 수: 1건
- 최종 Payment 상태: `SUCCESS`
- 최종 Order 상태: `COMPLETED`

실행 예시는 다음과 같다.

```bash
BASE_URL=http://localhost:8080 \
VUS=50 \
k6 run k6/payment-confirm-concurrency-cas.js
```

`k6`가 설치되어 있지 않은 환경에서는 `curl` 병렬 요청으로도 같은 핵심 결과를 확인할 수 있다.

```bash
BASE_URL=http://localhost:8080
FIXTURE=$(curl -s -X POST "$BASE_URL/test/payment-confirm-concurrency/fixtures")
PAYMENT_ID=$(node -e "const data=JSON.parse(process.argv[1]); console.log(data.paymentId)" "$FIXTURE")
PAYMENT_KEY=$(node -e "const data=JSON.parse(process.argv[1]); console.log(data.paymentKey)" "$FIXTURE")
ORDER_ID=$(node -e "const data=JSON.parse(process.argv[1]); console.log(data.orderId)" "$FIXTURE")
AMOUNT=$(node -e "const data=JSON.parse(process.argv[1]); console.log(data.amount)" "$FIXTURE")

seq 1 50 | xargs -P 50 -I {} curl -s -o /tmp/payment-confirm-{}.json -w "%{http_code}\n" \
  -X POST "$BASE_URL/api/payments/confirm" \
  -H "Content-Type: application/json" \
  -d "{\"paymentKey\":\"$PAYMENT_KEY\",\"paymentId\":\"$PAYMENT_ID\",\"orderId\":$ORDER_ID,\"amount\":$AMOUNT}"

curl -s "$BASE_URL/test/payment-confirm-concurrency/toss-stats"
curl -s "$BASE_URL/test/payment-confirm-concurrency/fixtures/$PAYMENT_ID/result"
```

## HTTP 병렬 검증 결과

실행 환경:

- 실행 일시: 2026-08-23 22:47 KST
- 실행 방식: `curl` 병렬 요청
- Profile: `dev`
- Toss mode: `mock`
- VUS: 50
- DB: 로컬 MySQL
- Hikari maximum-pool-size: 30
- `spring.jpa.open-in-view`: `false`

1차 실행 결과:

```text
50 500
mock Toss confirm count: 0
Payment status: NOT_STARTED
Order status: EXECUTING
```

원인은 CAS 실패가 아니라 Open Session In View와 자기 HTTP 호출 구조가 결합된 DB connection pool starvation이었다. `/api/payments/confirm` 요청들이 Payment 조회 후 같은 서버의 `/api/orders/{id}`를 다시 호출하면서 외부 요청과 내부 요청이 같은 Hikari pool을 경쟁했고, 내부 order 조회가 connection timeout으로 실패했다.

`spring.jpa.open-in-view=false` 적용 후 재실행 결과:

```text
1  200
49 409
mock Toss confirm count: 1
Payment status: SUCCESS
Order status: COMPLETED
stock: product1 100 -> 98, product2 100 -> 99
```

최종적으로 같은 `paymentId`에 대한 50개 병렬 HTTP 요청에서 승인 흐름은 1건만 실행되었고, 나머지 49건은 CAS 실패로 `409 Conflict`를 반환했다. Toss confirm과 재고 차감도 1회만 수행되어 CAS 설계가 실제 HTTP 진입점에서도 의도대로 동작함을 확인했다.
