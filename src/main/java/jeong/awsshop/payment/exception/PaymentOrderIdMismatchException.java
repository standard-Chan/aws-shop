package jeong.awsshop.payment.exception;

public class PaymentOrderIdMismatchException extends PaymentException {

    public PaymentOrderIdMismatchException(Long expectedOrderId, Long actualOrderId) {
        super("[Payment] Order id가 일치하지 않습니다. expected=" + expectedOrderId + ", actual=" + actualOrderId);
    }
}
