package jeong.awsshop.payment.exception;

public class DuplicatePaymentException extends PaymentException {

    public DuplicatePaymentException(Long orderId) {
        super("[Payment] 중복 결제 생성 요청입니다. orderId=" + orderId);
    }

    public DuplicatePaymentException(Long orderId, Throwable cause) {
        super("[Payment] 중복 결제 생성 요청입니다. orderId=" + orderId, cause);
    }
}
