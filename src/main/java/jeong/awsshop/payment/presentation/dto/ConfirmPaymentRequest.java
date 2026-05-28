package jeong.awsshop.payment.presentation.dto;

import java.math.BigDecimal;

public record ConfirmPaymentRequest(
    Long paymentId,
    String paymentKey,
    Long orderId,
    BigDecimal amount
) {
}
