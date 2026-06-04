package jeong.awsshop.eventpipeline.producer.controller.dto;

import jakarta.validation.constraints.Positive;

public record ProductViewEventRequest(
        @Positive Long userId,
        @Positive Long productId,
        @Positive Long searchEventId,
        String searchKeyword
) {
}
