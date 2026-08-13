# STOCK

## 개요
- `stock` 도메인은 상품 재고 수량 조회, 재고 차감, 재고 추가, 재고 변경 이력 확장을 담당한다.
- 현재 구현 범위는 특정 제품의 재고 `N`개 차감과 특정 제품의 재고 `N`개 추가다.
- 주문과 결제 흐름에서는 상품 판매 가능 여부와 주문 수량만큼의 재고 확보 여부를 확인하는 경계로 사용한다.

## 패키지 구조
- `src/main/java/jeong/awsshop/stock/domain`: 재고 엔티티, 값 객체, 저장소 계약
- `src/main/java/jeong/awsshop/stock/application`: 재고 조회, 예약, 차감, 복구 유스케이스
- `src/main/java/jeong/awsshop/stock/presentation`: 재고 관리 API와 요청/응답 DTO
- `src/main/java/jeong/awsshop/stock/infrastructure`: JPA 구현, 벌크 업데이트, 외부 시스템 연동 어댑터
- `src/main/java/jeong/awsshop/stock/exception`: 재고 부족, 재고 없음, 동시성 충돌 같은 도메인 예외

## 구현 설계 기준
- 재고는 상품당 하나의 `Stock` row로 관리한다.
- `stock.product_id`는 PK이자 `product.id` FK다.
- `Stock`은 `Product`를 `@OneToOne(fetch = LAZY)`, `@MapsId`로 참조한다.
- 재고 수량 타입은 `int`이며 0 이상을 유지한다.
- 재고 변경 요청 수량 `N`은 항상 양수여야 한다.
- 재고 차감은 `quantity >= N` 조건부 update로 원자성을 보장한다.
- 재고 추가 요청에서 `Stock` row가 없으면 기존 `Product`를 확인한 뒤 새 `Stock`을 생성한다.
- 재고 차감 요청에서 `Stock` row가 없으면 생성하지 않고 실패한다.
- 주문 생성 시점의 재고 예약, 결제 성공 시 확정 차감, 실패 시 복구는 이후 주문 연동 단계에서 별도 ADR로 확정한다.

## 서비스 계약
- `StockService.decrease(Long productId, int quantity)`: 특정 상품 재고를 차감하고 변경 후 재고를 반환한다.
- `StockService.increase(Long productId, int quantity)`: 특정 상품 재고를 추가하고 변경 후 재고를 반환한다.
- 서비스 응답은 `StockResponse(productId, quantity)`다.

## API 계약
- `POST /api/stocks/{productId}/decrease`: 요청 본문 `quantity`만큼 해당 상품 재고를 차감한다.
- `POST /api/stocks/{productId}/increase`: 요청 본문 `quantity`만큼 해당 상품 재고를 추가한다.
- 요청 본문은 `StockQuantityRequest(quantity)`다.
- 응답 본문은 `StockResponse(productId, quantity)`다.
- 상세 요청/응답 예시는 [../api/stock-controller-api.md](/mnt/c/Users/정석찬/Desktop/project/aws-shop/docs/api/stock-controller-api.md)를 따른다.

## 예외 기준
- `InvalidStockQuantityException`: 재고 변경 수량이 0 이하일 때
- `StockNotFoundException`: 차감 대상 재고 row가 없을 때
- `StockProductNotFoundException`: 추가 대상 상품이 없을 때
- `InsufficientStockException`: 현재 재고가 차감 요청 수량보다 작을 때
- `StockQuantityOverflowException`: 추가 후 재고가 `int` 범위를 초과할 때

## 다음 구현 전에 확인할 파일
- `src/main/java/jeong/awsshop/product/domain`
- `src/main/java/jeong/awsshop/order/application/OrderService.java`
- `src/main/java/jeong/awsshop/order/domain/Order.java`
- `src/main/java/jeong/awsshop/payment/application/PaymentService.java`
- `docs/order/ORDER.md`
- `docs/payment/PAYMENT.md`

## 테스트 기준
- domain 테스트는 재고 추가, 차감, 음수 방지, 부족 예외, overflow 예외를 검증한다.
- service 테스트는 재고 차감 성공, 재고 없음, 재고 부족, 재고 추가, 재고 row 생성, 상품 없음 예외를 검증한다.
- repository 테스트는 조건부 차감 update와 부족 시 수량 불변식을 검증한다.
- controller 테스트는 재고 차감/추가 요청 위임, 응답 JSON, 예외별 HTTP 상태 코드 매핑을 검증한다.
