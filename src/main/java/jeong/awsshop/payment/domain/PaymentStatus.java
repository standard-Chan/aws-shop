package jeong.awsshop.payment.domain;

public enum PaymentStatus {

    NOT_STARTED, // 결제 시작 전
    EXECUTING, // 결제 진행 중
    SUCCESS, // 결제 성공
    FAILED // 결제 실패
}
