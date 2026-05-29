package jeong.awsshop.analytics.presentation.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsFunnelResponse(
        Instant from,
        Instant to,
        String basis,
        List<AnalyticsFunnelStepResponse> steps
) {
}
