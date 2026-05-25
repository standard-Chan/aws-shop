package jeong.awsshop.payment.exception;

public class PaymentTossPaymentProcessingException extends PaymentException {

    public PaymentTossPaymentProcessingException(Long paymentId, String paymentKey, String message, Throwable cause) {
        super("[Payment] toss payment 결제 승인 요청에 실패했습니다." + message + "paymentId=" + paymentId + ", paymentKey=" + paymentKey,
            cause);
    }
}
