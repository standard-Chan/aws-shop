package jeong.awsshop.payment.exception;

public class DuplicatePaymentException extends PaymentException {

    public DuplicatePaymentException(Long orderId) {
        super("[Payment] 해당 주문에 대한 결제가 이미 진행중입니다. orderId=" + orderId);
    }
}
