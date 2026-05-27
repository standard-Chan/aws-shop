package jeong.awsshop.payment.presentation.dto;

import java.math.BigDecimal;

public record ConfirmPaymentRequest(
    String paymentKey, Long paymentId, Long orderId, BigDecimal amount
) {

}
