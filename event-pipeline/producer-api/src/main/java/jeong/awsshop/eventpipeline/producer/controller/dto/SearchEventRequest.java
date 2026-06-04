package jeong.awsshop.eventpipeline.producer.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SearchEventRequest(
        @Positive Long userId,
        @NotBlank String keyword
) {
}
