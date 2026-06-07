package jeong.awsshop.eventpipeline.monolith.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ProductViewEventRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long productId,
        @Positive Long searchEventId,
        @Pattern(regexp = ".*\\S.*")
        String searchKeyword
) {
}
