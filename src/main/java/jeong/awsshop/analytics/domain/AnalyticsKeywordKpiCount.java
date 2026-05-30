package jeong.awsshop.analytics.domain;

public interface AnalyticsKeywordKpiCount {

    String getKeyword();

    long getSearchCount();

    long getProductViewCount();
}
