package jeong.awsshop.payment.exception;

public class PaymentRecoveryRequiredException extends PaymentException {

    public PaymentRecoveryRequiredException(Long orderId) {
        super("[Payment] 결제 처리 상태를 복구했습니다. 다시 시도해주세요. orderId=" + orderId);
    }
}
