package jeong.awsshop.eventpipeline.monolith.presentation.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

public record AddToCartEventRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long productId
) {
}
