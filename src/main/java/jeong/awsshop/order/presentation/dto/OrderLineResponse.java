package jeong.awsshop.order.presentation.dto;

import java.math.BigDecimal;
import jeong.awsshop.order.domain.OrderLine;

public record OrderLineResponse(
    Long productId,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineAmount
) {

    public static OrderLineResponse from(OrderLine line) {
        return new OrderLineResponse(
            line.getProductId(),
            line.getQuantity(),
            line.getUnitPrice(),
            line.getLineAmount()
        );
    }
}
