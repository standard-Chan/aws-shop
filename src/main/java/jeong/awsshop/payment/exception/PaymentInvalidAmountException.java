package jeong.awsshop.payment.exception;

import java.math.BigDecimal;

public class PaymentInvalidAmountException extends PaymentException {

    public PaymentInvalidAmountException(BigDecimal amount) {
        super("[Payment] 결제 금액이 유효하지 않습니다. amount=" + amount);
    }
}
