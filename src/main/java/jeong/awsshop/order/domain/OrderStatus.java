package jeong.awsshop.order.domain;

public enum OrderStatus {
    NOT_STARTED, // 주문 시작 전
    PROCESSING, // 주문 처리 중
    PENDING, // 결제 처리 실패 후 후속 처리를 대기 중인 상태
    COMPLETED,    // 주문 완료
    CANCELED  // 주문 취소
}
