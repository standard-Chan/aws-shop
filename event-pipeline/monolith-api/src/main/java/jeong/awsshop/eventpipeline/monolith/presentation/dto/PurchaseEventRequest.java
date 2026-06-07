package jeong.awsshop.eventpipeline.monolith.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseEventRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long orderId
) {
}
