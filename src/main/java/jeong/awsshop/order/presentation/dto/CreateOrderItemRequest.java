package jeong.awsshop.order.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
    @NotNull
    @Positive
    Long productId,

    @Positive
    int quantity
) {
}
