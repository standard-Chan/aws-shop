package jeong.awsshop.payment.infrastructure.tosspayment.dto;

import java.math.BigDecimal;

public record TossPaymentConfirmRequest (
    Long paymentId,
    String paymentKey,
    String orderId,
    BigDecimal amount
) {
}
