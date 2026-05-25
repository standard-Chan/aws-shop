package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class PaymentOrderLookupException extends PaymentException {

    public PaymentOrderLookupException(Long orderId) {
        super("[Payment - Order] Order 서버와의 통신이 실패했습니다. orderId=" + orderId);
    }

    public PaymentOrderLookupException(Long orderId, Throwable cause) {
        super("[Payment-Order] Order 서버와의 통신이 실패했습니다. orderId=" + orderId, cause);
    }

    public PaymentOrderLookupException(Long orderId, String message) {
        super("[Payment-Order] Order 서버와의 통신이 실패했습니다. " + message + " orderId=" + orderId);
    }
}
