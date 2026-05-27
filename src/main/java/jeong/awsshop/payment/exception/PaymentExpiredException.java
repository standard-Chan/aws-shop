package jeong.awsshop.payment.exception;

public class PaymentExpiredException extends PaymentException {

    public PaymentExpiredException(Long orderId, Long paymentId) {
        super("[Payment] 결제가 만료되었습니다. orderId=" + orderId + ", paymentId=" + paymentId);
    }
}
