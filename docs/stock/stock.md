# STOCK

## 개요
- `stock` 도메인은 상품 재고 수량 조회, 재고 차감, 재고 복구, 재고 변경 이력 확장을 담당한다.
- 주문과 결제 흐름에서는 상품 판매 가능 여부와 주문 수량만큼의 재고 확보 여부를 확인하는 경계로 사용한다.
- 현재 변경 범위는 도메인 패키지와 문서 골격 추가다. 실제 엔티티, API, 테스트는 이후 구현 단계에서 추가한다.

## 패키지 구조
- `src/main/java/jeong/awsshop/stock/domain`: 재고 엔티티, 값 객체, 저장소 계약
- `src/main/java/jeong/awsshop/stock/application`: 재고 조회, 예약, 차감, 복구 유스케이스
- `src/main/java/jeong/awsshop/stock/presentation`: 재고 관리 API와 요청/응답 DTO
- `src/main/java/jeong/awsshop/stock/infrastructure`: JPA 구현, 벌크 업데이트, 외부 시스템 연동 어댑터
- `src/main/java/jeong/awsshop/stock/exception`: 재고 부족, 재고 없음, 동시성 충돌 같은 도메인 예외

## 초기 설계 기준
- 재고는 상품 ID 기준으로 관리한다.
- 주문 생성 시점에는 재고 검증과 예약을 분리해 중복 차감을 막는다.
- 결제 성공 시 예약 재고를 확정 차감하고, 결제 실패 또는 주문 취소 시 예약 재고를 복구하는 흐름을 기본으로 검토한다.
- 대량 상품 데이터 적재와 실시간 주문 재고 변경은 쓰기 패턴이 다르므로 저장소 메서드를 분리한다.
- 동시성 제어는 낙관적 락, 조건부 update, 재고 예약 테이블 중 실제 주문 흐름에 맞는 방식을 ADR로 확정한다.

## 다음 구현 전에 확인할 파일
- `src/main/java/jeong/awsshop/product/domain`
- `src/main/java/jeong/awsshop/order/application/OrderService.java`
- `src/main/java/jeong/awsshop/order/domain/Order.java`
- `src/main/java/jeong/awsshop/payment/application/PaymentService.java`
- `docs/order/ORDER.md`
- `docs/payment/PAYMENT.md`

## 테스트 기준
- service 테스트는 재고 조회, 예약, 차감, 복구, 재고 부족 예외를 검증한다.
- repository 테스트는 동시 차감 조건부 update와 재고 수량 불변식을 검증한다.
- controller 테스트는 관리 API의 요청 검증, 응답 코드, 예외 매핑을 검증한다.
- 주문/결제 연동이 추가되면 Order와 Payment 테스트에서 재고 예약 및 복구 분기를 함께 검증한다.
