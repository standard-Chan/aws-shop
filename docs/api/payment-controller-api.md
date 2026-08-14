# PaymentController API 문서

`PaymentController`는 결제 생성과 결제 승인 API를 제공한다.

- Base URL: `/api/payments`
- Content-Type: `application/json`
- Controller 위치: `src/main/java/jeong/awsshop/payment/presentation/PaymentController.java`

## 공통 규칙

### 요청 바디 검증

- `CreatePaymentRequest`, `ConfirmPaymentRequest`에는 현재 Bean Validation이 없다.
- 따라서 `null`, 음수, 형식 오류를 컨트롤러 계층에서 명시적으로 검증하지 않는다.
- JSON 역직렬화가 불가능한 경우를 제외하면, 대부분의 입력 오류는 service 또는 하위 도메인 로직에서 실패한다.

### 예외 응답

- `payment` 도메인 전용 예외 응답 매핑은 아직 없다.
- `ConstraintViolationException`만 전역적으로 `400 Bad Request`로 변환된다.
- 현재 `PaymentException`, `PaymentOrderLookupException`, `PaymentConfirmExternalException`, `PaymentNotFoundException` 등은 별도 상태 코드 매핑이 없어 기본적으로 5xx 계열 응답으로 전파될 수 있다.

## 1. 결제 생성

### 요청

- Method: `POST`
- URL: `/api/payments`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `orderId` | long | Y | 결제를 생성할 주문 ID |

### 동작 메모

- controller는 요청 본문을 `CreatePaymentRequest`로 받아 `PaymentService.createPayment()`에 전달한다.
- service는 주문 서비스를 조회해 주문 총액을 확인한 뒤 `Payment` 엔티티를 생성한다.
- 같은 `orderId`에 기존 활성 결제(`NOT_STARTED`, `EXECUTING`)가 있으면 기존 활성 결제를 `FAILED`로 변경하고 새 결제를 생성한다.
- 기존 `FAILED`, `EXPIRED`, `SUCCESS` 결제는 이력 보존을 위해 변경하지 않는다.
- 생성되는 결제 상태는 `NOT_STARTED`다.
- 응답의 `amount`는 요청 본문이 아니라 주문 서비스에서 조회한 주문 총액이다.

### 요청 예시

```json
{
  "orderId": 123
}
```

### 응답 예시

```json
{
  "paymentId": 1,
  "orderId": 123,
  "status": "NOT_STARTED",
  "amount": 100.00
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | long | 결제 ID |
| `orderId` | long | 주문 ID |
| `status` | string | 결제 상태. 현재 생성 직후 값은 `NOT_STARTED` |
| `amount` | number | 결제 금액 |

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/payments' \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId": 123
  }'
```

### 상태 코드

| 상태 코드 | 설명 |
| --- | --- |
| `200 OK` | 결제 생성 성공 |
| `5xx` | 주문 조회 실패 또는 결제 생성 중 런타임 예외 발생 |

## 2. 결제 승인

### 요청

- Method: `POST`
- URL: `/api/payments/confirm`

### Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `paymentId` | long | Y | 내부 결제 ID |
| `paymentKey` | string | Y | 결제 승인 시도를 식별하는 중복 불가 키. 결제 승인 멱등키로 사용한다. |
| `orderId` | long | Y | 주문 ID |
| `amount` | number | Y | 승인 요청 금액 |

### 동작 메모

- controller는 요청 본문을 `ConfirmPaymentRequest`로 받아 `PaymentService.confirmPayment()`에 전달한다.
- service는 먼저 `paymentId`로 내부 결제 정보를 조회한다.
- 승인 시작 전 confirm 진입 시점의 현재 시각으로 결제 만료 여부를 먼저 확인한다.
- 결제가 만료되었으면 내부 결제 상태를 `EXPIRED`로 저장하고 주문을 `PENDING`으로 되돌린 뒤 `PaymentExpiredException`을 던진다. 이 경우 `paymentKey` 등록, 재고 예약, Toss 승인 요청, 주문 완료 처리는 실행하지 않는다.
- 만료되지 않은 결제는 내부 결제 상태를 진행 중으로 변경한 뒤, 주문 ID와 금액을 검증한다.
- `paymentKey`는 결제 승인 시도를 분류하는 키이며, 같은 `paymentKey`의 중복 사용은 허용하지 않는다.
- 서버는 `paymentKey`를 내부 `Payment.paymentKey`에 저장하고, 결제 승인 재시도/중복 요청을 구분하기 위한 멱등키로 사용한다.
- 현재 구현에서는 `paymentKey`가 외부 결제사 승인 요청에도 전달된다. 외부 결제 연동 환경에서는 호출자가 결제창 또는 외부 결제 인증 완료 후 받은 유효한 키를 그대로 전달해야 한다.
- Toss 승인 요청 DTO에서는 내부 `paymentId`를 Toss의 `orderId` 값으로 사용한다.
- 검증 후 `orderId`로 주문 요약을 조회하고, 주문의 모든 `items` 라인에 대해 `StockService.decrease(productId, quantity)`로 재고를 예약한다.
- 재고 예약이 성공하면 Toss 결제 승인 API를 호출하고, 성공 시 결제 상태를 완료로 변경한다.
- 승인 성공 후 주문 서비스에 완료 상태 업데이트를 요청한다.
- 재고 예약 실패 시 Toss 결제 승인 API를 호출하지 않고 내부 결제 상태를 실패로 변경한다.
- 일부 주문 라인 재고 예약 후 다음 라인 예약이 실패하면 이미 예약한 라인을 `StockService.increase(productId, quantity)`로 복구한다.
- 재고 예약 이후 승인 실패가 발생하면 예약한 모든 주문 라인을 `StockService.increase(productId, quantity)`로 복구한다.
- 승인 실패 시 내부 결제 상태를 실패로 변경하고 주문 서비스에 pending 상태 업데이트를 요청한 뒤 예외를 다시 던진다.

### 요청 예시

```json
{
  "paymentId": 1,
  "paymentKey": "payment-key-1",
  "orderId": 123,
  "amount": 100.00
}
```

### 응답 예시

```json
{
  "paymentKey": "payment-key-1",
  "orderId": "123",
  "method": "CARD",
  "status": "DONE",
  "totalAmount": 100,
  "requestedAt": "2026-05-25T10:15:30+09:00",
  "approvedAt": "2026-05-25T10:16:00+09:00"
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `paymentKey` | string | 결제 승인 시도 식별 키 |
| `orderId` | string | 외부 결제사 응답의 주문 ID |
| `method` | string | 결제 수단 |
| `status` | string | 외부 결제 상태. 예: `DONE`, `CANCELED`, `ABORTED` |
| `totalAmount` | long | 승인된 총 결제 금액 |
| `requestedAt` | string | 승인 요청 시각, ISO-8601 offset datetime |
| `approvedAt` | string | 승인 완료 시각, ISO-8601 offset datetime |

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/payments/confirm' \
  -H 'Content-Type: application/json' \
  -d '{
    "paymentId": 1,
    "paymentKey": "payment-key-1",
    "orderId": 123,
    "amount": 100.00
  }'
```

### 상태 코드

| 상태 코드 | 설명 |
| --- | --- |
| `200 OK` | 결제 승인 성공 |
| `410 Gone` | confirm 진입 시점에 내부 결제가 만료되어 승인 처리를 시작하지 않음 |
| `5xx` | 결제 조회 실패, 주문 ID/금액 검증 실패, 재고 예약 실패, 외부 결제 승인 실패, 주문 상태 업데이트 실패 등 런타임 예외 발생 |
