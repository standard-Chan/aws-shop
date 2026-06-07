package jeong.awsshop.eventpipeline.monolith.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SearchEventRequest(
        @NotNull @Positive Long userId,
        @NotBlank String keyword
) {
}
