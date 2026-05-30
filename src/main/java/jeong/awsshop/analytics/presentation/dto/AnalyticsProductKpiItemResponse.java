package jeong.awsshop.analytics.presentation.dto;

public record AnalyticsProductKpiItemResponse(
        Long productId,
        long productViewCount,
        long addToCartCount,
        double cartRate,
        Double purchaseRate
) {
}
