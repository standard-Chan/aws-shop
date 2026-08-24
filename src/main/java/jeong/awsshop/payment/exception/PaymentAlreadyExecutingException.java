package jeong.awsshop.payment.exception;

public class PaymentAlreadyExecutingException extends PaymentException {

    public PaymentAlreadyExecutingException(Long paymentId) {
        super("[Payment] 이미 승인 처리 중인 결제입니다. paymentId=" + paymentId);
    }
}
