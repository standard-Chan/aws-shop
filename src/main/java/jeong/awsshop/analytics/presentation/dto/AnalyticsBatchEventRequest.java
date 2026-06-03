package jeong.awsshop.analytics.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalyticsBatchEventRequest(
        @NotEmpty
        @Size(max = 1000)
        List<@Valid AnalyticsBatchEventRequestItem> events
) {
}
