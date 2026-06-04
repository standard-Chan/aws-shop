package jeong.awsshop.eventpipeline.producer.controller.dto;

import jakarta.validation.constraints.Positive;

public record PurchaseEventRequest(
        @Positive Long userId,
        @Positive Long orderId
) {
}
