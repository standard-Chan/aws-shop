package jeong.awsshop.payment.exception;

import java.math.BigDecimal;

public class PaymentAmountMismatchException extends PaymentException {

    public PaymentAmountMismatchException(BigDecimal expectedAmount, BigDecimal actualAmount) {
        super("[Payment] 결제 금액이 일치하지 않습니다. expected=" + expectedAmount + ", actual=" + actualAmount);
    }
}
