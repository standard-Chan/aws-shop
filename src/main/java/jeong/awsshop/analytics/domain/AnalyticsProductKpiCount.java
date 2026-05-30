package jeong.awsshop.analytics.domain;

public interface AnalyticsProductKpiCount {

    Long getProductId();

    long getProductViewCount();

    long getAddToCartCount();
}
