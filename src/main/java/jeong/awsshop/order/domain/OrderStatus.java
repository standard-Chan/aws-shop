package jeong.awsshop.order.domain;

public enum OrderStatus {
    NOT_STARTED, // 주문 시작 전
    EXECUTING, // 주문 처리 중
    COMPLETED,    // 주문 완료
    CANCELED  // 주문 취소
}
