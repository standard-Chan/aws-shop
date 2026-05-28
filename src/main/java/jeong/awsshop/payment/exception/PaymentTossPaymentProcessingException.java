package jeong.awsshop.payment.exception;

public class PaymentTossPaymentProcessingException extends PaymentException {

    public PaymentTossPaymentProcessingException(Long paymentId, String paymentKey, String message, Throwable cause) {
        super("[Payment] Toss 결제 승인 요청에 실패했습니다. paymentId=" + paymentId
                + ", paymentKey=" + paymentKey + ", message=" + message,
            cause);
    }
}
