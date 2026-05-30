package jeong.awsshop.analytics.presentation.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsKeywordKpiResponse(
        Instant from,
        Instant to,
        String basis,
        List<AnalyticsKeywordKpiItemResponse> items
) {
}
