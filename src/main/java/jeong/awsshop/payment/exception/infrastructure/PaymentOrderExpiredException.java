package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class PaymentOrderExpiredException extends PaymentException {

    public PaymentOrderExpiredException(Long orderId, Throwable cause) {
        super("[Payment-Order] 만료된 주문입니다. orderId=" + orderId, cause);
    }
}
