package jeong.awsshop.payment.exception;

public class PaymentConfirmExternalException extends PaymentException {

    public PaymentConfirmExternalException(Long paymentId, String paymentKey, Throwable cause) {
        super("[Payment] 외부 결제 승인 요청에 실패했습니다. paymentId=" + paymentId + ", paymentKey=" + paymentKey,
            cause);
    }
}
