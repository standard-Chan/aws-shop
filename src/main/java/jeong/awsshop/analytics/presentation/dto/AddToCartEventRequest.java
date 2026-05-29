package jeong.awsshop.analytics.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddToCartEventRequest(
        @NotNull
        @Positive Long userId,
        @NotNull
        @Positive Long productId
) {
}
