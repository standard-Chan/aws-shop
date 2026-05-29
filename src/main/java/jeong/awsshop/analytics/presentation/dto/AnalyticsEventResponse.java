package jeong.awsshop.analytics.presentation.dto;

import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventType;

public record AnalyticsEventResponse(
        Long eventId,
        AnalyticsEventType eventType
) {

    public static AnalyticsEventResponse from(AnalyticsEventMessage event) {
        return new AnalyticsEventResponse(event.eventId(), event.eventType());
    }
}
