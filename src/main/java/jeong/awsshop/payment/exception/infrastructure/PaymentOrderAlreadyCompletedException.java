package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class PaymentOrderAlreadyCompletedException extends PaymentException {

    public PaymentOrderAlreadyCompletedException(Long orderId, Throwable cause) {
        super("[Payment-Order] 이미 완료된 주문입니다. orderId=" + orderId, cause);
    }
}
