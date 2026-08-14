package jeong.awsshop.payment.infrastructure.order.dto;

import java.math.BigDecimal;

public record OrderLineSummary(
    Long productId,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineAmount
) {
}
