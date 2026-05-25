package jeong.awsshop.payment.exception;

public class PaymentInvalidPaymentKey extends PaymentException {

    public PaymentInvalidPaymentKey(String paymentKey) {
        super("[Payment] PaymentKey가 유효하지 않습니다." + paymentKey);
    }
}