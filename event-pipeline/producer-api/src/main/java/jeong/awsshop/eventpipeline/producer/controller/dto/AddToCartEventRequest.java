package jeong.awsshop.eventpipeline.producer.controller.dto;

import jakarta.validation.constraints.Positive;

public record AddToCartEventRequest(
        @Positive Long userId,
        @Positive Long productId
) {
}
