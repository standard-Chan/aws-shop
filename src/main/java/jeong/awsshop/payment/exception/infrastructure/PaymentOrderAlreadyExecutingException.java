package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class PaymentOrderAlreadyExecutingException extends PaymentException {

    public PaymentOrderAlreadyExecutingException(Long orderId, Throwable cause) {
        super("[Payment-Order] 이미 진행 중인 결제가 존재합니다. orderId=" + orderId, cause);
    }
}
