package jeong.awsshop.analytics.presentation.dto;

public record AnalyticsKeywordKpiItemResponse(
        String keyword,
        long searchCount,
        long productViewCount,
        double searchCtr
) {
}
