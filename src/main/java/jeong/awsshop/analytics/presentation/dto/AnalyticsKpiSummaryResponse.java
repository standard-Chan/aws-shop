package jeong.awsshop.analytics.presentation.dto;

import java.time.Instant;

public record AnalyticsKpiSummaryResponse(
        Instant from,
        Instant to,
        String basis,
        long searchCount,
        long productViewCount,
        long addToCartCount,
        long purchaseCount,
        double searchCtr,
        double cartRate,
        double purchaseRate
) {
}
