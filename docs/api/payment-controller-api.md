# PaymentController API 문서

`PaymentController`는 결제 생성과 결제 승인 API를 제공한다.

- Base URL: `/api/payments`
- Content-Type: `application/json`
- Controller 위치: `src/main/java/jeong/awsshop/payment/presentation/PaymentController.java`

## 공통 규칙

### 요청 바디 검증

- `CreatePaymentRequest`, `TossPaymentConfirmRequest`에는 현재 Bean Validation이 없다.
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
| `paymentKey` | string | Y | 외부 결제사 결제 키 |
| `orderId` | long | Y | 주문 ID |
| `amount` | number | Y | 승인 요청 금액 |

### 동작 메모

- controller는 요청 본문을 `TossPaymentConfirmRequest`로 받아 `PaymentService.confirmPayment()`에 전달한다.
- service는 먼저 `paymentId`로 내부 결제 정보를 조회한다.
- 승인 시작 시 내부 결제 상태를 진행 중으로 변경한 뒤, 주문 ID와 금액을 검증한다.
- 검증 후 Toss 결제 승인 API를 호출하고, 성공 시 결제 상태를 완료로 변경한다.
- 승인 성공 후 주문 서비스에 완료 상태 업데이트를 요청한다.
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
| `paymentKey` | string | 외부 결제 키 |
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
| `5xx` | 결제 조회 실패, 주문 ID/금액 검증 실패, 외부 결제 승인 실패, 주문 상태 업데이트 실패 등 런타임 예외 발생 |
