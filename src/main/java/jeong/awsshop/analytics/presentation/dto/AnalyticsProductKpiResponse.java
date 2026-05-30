package jeong.awsshop.analytics.presentation.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsProductKpiResponse(
        Instant from,
        Instant to,
        String basis,
        List<AnalyticsProductKpiItemResponse> items
) {
}
