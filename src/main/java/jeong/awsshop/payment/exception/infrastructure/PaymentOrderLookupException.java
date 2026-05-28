package jeong.awsshop.payment.exception.infrastructure;

import jeong.awsshop.payment.exception.PaymentException;

public class PaymentOrderLookupException extends PaymentException {

    public PaymentOrderLookupException(Long orderId) {
        super("[Payment] 주문 조회에 실패했습니다. orderId=" + orderId);
    }

    public PaymentOrderLookupException(Long orderId, Throwable cause) {
        super("[Payment] 주문 조회에 실패했습니다. orderId=" + orderId, cause);
    }

    public PaymentOrderLookupException(Long orderId, String message) {
        super("[Payment] 주문 처리 중 오류가 발생했습니다. " + message + " orderId=" + orderId);
    }
}
