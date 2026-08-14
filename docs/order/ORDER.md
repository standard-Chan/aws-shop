# Order 처리 흐름

## 현재 구현 범위
- 현재 `order` 도메인은 주문 생성, 주문 요약 조회, 결제 연동을 위한 상태 갱신 API를 제공한다.
- 주문 생성은 요청으로 받은 상품 ID와 수량을 기준으로 상품 가격을 조회하고 주문 금액을 계산한다.
- 주문 생성 시 재고 row 존재와 요청 수량 충족 여부를 확인하지만, 재고 차감이나 예약은 아직 수행하지 않는다.
- 상품 가격이 `null`이면 주문 계산에서는 0원으로 처리한다.
- 결제 생성과 승인은 `payment` 도메인이 주도하고, `order` 도메인은 결제 흐름에 필요한 주문 상태를 갱신하는 경계 역할을 한다.

## 주문 상태 모델
| 상태 | 의미 | 비고 |
| --- | --- | --- |
| `NOT_STARTED` | 주문 생성 직후 상태 | 결제 생성 진입 전 |
| `EXECUTING` | 결제 생성 또는 승인 흐름이 진행 중인 상태 | 중복 결제 생성을 막기 위한 선점 상태 |
| `PENDING` | 결제 실패 후 후속 처리를 기다리는 상태 | 재시도, 취소, 복구 정책 확장 지점 |
| `COMPLETED` | 주문 완료 | 종료 상태 |
| `CANCELED` | 주문 취소 또는 실패 | 종료 상태 |
| `EXPIRED` | 주문 만료 | 종료 상태 |

종료 상태인 `COMPLETED`, `CANCELED`, `EXPIRED` 주문은 다른 상태로 전이할 수 없다.

## API 흐름

### 주문 생성
- API: `POST /api/orders`
- 요청은 `items` 배열로 상품 ID와 수량을 받는다.
- 같은 상품 ID가 여러 번 들어오면 수량을 합산해 하나의 주문 라인으로 저장한다.
- 상품별 `lineAmount = unitPrice * quantity`로 계산하고 주문 `totalAmount`는 주문 라인 금액 합계다.
- 상품 가격이 `null`이면 `unitPrice=0`, `lineAmount=0`으로 계산한다.
- 임시 사용자 ID `1`과 임시 배송지 `Seoul Songpa-gu Olympic-ro 300`은 유지한다.
- 상태는 `NOT_STARTED`로 저장한다.
- 응답은 `OrderSummaryResponse`이며 `orderId`, `userId`, `status`, `totalAmount`, `shippingAddress`, `items`를 포함한다.
- `items`는 `productId`, `quantity`, `unitPrice`, `lineAmount`를 포함한다.

### 주문 조회
- API: `GET /api/orders/{id}`
- 저장된 주문을 조회해 `OrderSummaryResponse`로 반환한다.
- 주문이 없으면 `OrderNotFoundException`을 던지고 HTTP `404 Not Found`로 응답한다.

### 결제 생성 진입 선점
- API: `POST /api/orders/{id}/executing`
- 결제 생성 전 Payment가 호출하는 주문 선점 API다.
- repository native update로 현재 상태가 `EXECUTING`, `COMPLETED`, `CANCELED`, `EXPIRED`가 아닌 경우에만 `EXECUTING`으로 변경한다.
- update count가 `1`이면 선점 성공으로 보고 변경된 주문 요약을 반환한다.
- update count가 `0`이면 주문을 다시 조회해 현재 상태별 예외를 반환한다.

상태별 응답 계약:

| 현재 상태 | 응답 | 헤더 |
| --- | --- | --- |
| `EXECUTING` | `409 Conflict` | `X-Order-Status: EXECUTING` |
| `COMPLETED` | `409 Conflict` | `X-Order-Status: COMPLETED` |
| `CANCELED` | `409 Conflict` | `X-Order-Status: CANCELED` |
| `EXPIRED` | `410 Gone` | `X-Order-Status: EXPIRED` |

### 결제 실패 후 대기
- API: `POST /api/orders/{id}/pending`
- 결제 검증 실패, 외부 결제 승인 실패, 활성 결제 복구 필요 상황에서 Payment가 호출한다.
- 주문 상태를 `PENDING`으로 변경한다.
- 종료 상태 주문이면 `OrderInvalidStatusTransitionException`을 던진다.

### 주문 완료
- API: `POST /api/orders/{id}/success`
- 결제 승인 성공 후 Payment가 호출한다.
- 주문 상태를 `COMPLETED`로 변경하고 `completedAt`을 현재 시각으로 기록한다.
- 종료 상태 주문이면 `OrderInvalidStatusTransitionException`을 던진다.

### 주문 실패 또는 취소
- API: `POST /api/orders/{id}/fail`
- 현재 구현에서는 주문 실패 상태 갱신 API로 사용한다.
- 주문 상태를 `CANCELED`로 변경한다.
- 종료 상태 주문이면 `OrderInvalidStatusTransitionException`을 던진다.

## 결제 연동 흐름
1. 사용자가 주문을 생성하면 주문은 `NOT_STARTED` 상태로 저장된다.
2. 결제 생성 요청이 들어오면 Payment가 `POST /api/orders/{id}/executing`을 호출한다.
3. Order가 `EXECUTING` 전환에 성공하면 Payment는 주문 총액을 기준으로 결제를 생성한다.
4. Order가 이미 `EXECUTING`이면 Payment는 같은 주문의 활성 결제를 조회해 기존 결제 재사용, 만료 처리, 복구 필요 여부를 판단한다.
5. 결제 승인 성공 시 Payment는 `POST /api/orders/{id}/success`를 호출해 주문을 `COMPLETED`로 변경한다.
6. 결제 승인 실패 또는 결제 복구 필요 시 Payment는 `POST /api/orders/{id}/pending`을 호출해 주문을 `PENDING`으로 변경한다.

현재 구조에서는 Order 상태 변경과 Payment 생성이 서로 다른 저장소와 트랜잭션 경계를 가진다. 따라서 `EXECUTING` 상태만으로 항상 활성 Payment row가 존재한다고 단정하지 않고, Payment 쪽에서 활성 결제 조회와 복구 분기를 함께 처리한다.

## 예외 및 응답 계약
- `OrderNotFoundException`: 주문 없음, HTTP `404 Not Found`
- `OrderProductNotFoundException`: 주문 생성 대상 상품 없음, HTTP `404 Not Found`
- `OrderStockNotFoundException`: 주문 생성 대상 상품 재고 row 없음, HTTP `404 Not Found`
- `OrderInsufficientStockException`: 주문 생성 대상 상품 재고 부족, HTTP `409 Conflict`
- `OrderAlreadyExecutingException`: 이미 처리 중인 주문, HTTP `409 Conflict`, `X-Order-Status: EXECUTING`
- `OrderAlreadyCompletedException`: 이미 완료된 주문, HTTP `409 Conflict`, `X-Order-Status: COMPLETED`
- `OrderAlreadyCanceledException`: 이미 취소된 주문, HTTP `409 Conflict`, `X-Order-Status: CANCELED`
- `OrderExpiredException`: 만료된 주문, HTTP `410 Gone`, `X-Order-Status: EXPIRED`
- `OrderInvalidStatusTransitionException`: 종료 상태에서의 잘못된 상태 전이, HTTP `409 Conflict`

## 확장 설계
현재 주문 생성은 요청 상품 기준 주문 라인 생성과 금액 계산까지 구현한다. 이후 실제 주문 생성 흐름은 다음 책임을 순서대로 추가한다.

1. 사용자 정보 검증 및 조회
2. 사용자 장바구니 조회
3. 상품 판매 가능 상태 검증
4. 배송지 검증 및 배송 정책 계산
5. 주문 생성 시점 재고 예약 또는 결제 성공 시점 재고 차감 정책 확정
6. 결제 완료 후 구매 이벤트 발행

상품별 구매율, 추천 학습, 구매 이벤트 정합성을 위해 주문 상품 라인 모델을 저장한다. 현재 analytics와 recommendation 문서에서 `PURCHASE` 이벤트는 아직 `orderId`만 가지고 있으므로, 구매 이벤트는 주문 라인을 기준으로 상품 ID를 함께 발행하는 방향으로 확장한다.

## 테스트 기준
- service 테스트는 주문 생성, 조회, 상태 전이, 종료 상태 전이 거부를 검증한다.
- controller 테스트는 API별 JSON 응답과 상태별 HTTP 응답 코드를 검증한다.
- 결제 연동 회귀는 Payment 테스트에서 `OrderClient` 호출 결과와 주문 상태 갱신 실패 분기를 함께 확인한다.
- 문서 계약을 검산할 때 우선 확인할 파일:
  - `src/test/java/jeong/awsshop/order/application/OrderServiceTest.java`
  - `src/test/java/jeong/awsshop/order/presentation/OrderControllerTest.java`
  - `src/main/java/jeong/awsshop/payment/application/PaymentService.java`
