package jeong.awsshop.analytics.presentation.dto;

import jeong.awsshop.analytics.domain.AnalyticsEventType;

public record AnalyticsFunnelStepResponse(
        AnalyticsEventType eventType,
        long count,
        Double conversionRateFromPrevious,
        double conversionRateFromSearch
) {
}
