package jeong.awsshop.payment.exception;

import jeong.awsshop.payment.domain.PaymentStatus;

public class PaymentInvalidStatusException extends PaymentException {

    public PaymentInvalidStatusException(PaymentStatus expected, PaymentStatus actual) {
        super("[Payment] 결제 상태가 올바르지 않습니다. expected=" + expected + ", actual=" + actual);
    }

    public PaymentInvalidStatusException(PaymentStatus expected, PaymentStatus actual, String message) {
        super("[Payment]" + message + " expected=" + expected + ", actual=" + actual);
    }
}
