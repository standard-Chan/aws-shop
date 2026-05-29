package jeong.awsshop.analytics.application;

import jeong.awsshop.analytics.domain.AnalyticsEventMessage;

public interface AnalyticsEventPublisher {

    void publish(AnalyticsEventMessage event);
}
