package jeong.awsshop.analytics.domain;

public interface AnalyticsEventTypeCount {

    AnalyticsEventType getEventType();

    long getCount();
}
