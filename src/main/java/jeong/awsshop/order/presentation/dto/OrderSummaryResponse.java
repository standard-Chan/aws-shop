package jeong.awsshop.order.presentation.dto;

import java.math.BigDecimal;

public record OrderSummaryResponse(Long orderId, BigDecimal totalAmount) {

}
