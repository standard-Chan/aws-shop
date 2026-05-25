package jeong.awsshop.payment.exception;

public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(Long paymentId) {
        super("[Payment] 결제 정보를 찾을 수 없습니다. paymentId=" + paymentId);
    }
}
