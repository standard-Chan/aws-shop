package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class PaymentOrderAlreadyCanceledException extends PaymentException {

    public PaymentOrderAlreadyCanceledException(Long orderId, Throwable cause) {
        super("[Payment-Order] 이미 실패한 주문입니다. orderId=" + orderId, cause);
    }
}
