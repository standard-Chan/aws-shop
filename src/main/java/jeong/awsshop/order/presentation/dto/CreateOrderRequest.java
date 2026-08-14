package jeong.awsshop.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
    @Valid
    @NotEmpty
    List<CreateOrderItemRequest> items
) {
}
