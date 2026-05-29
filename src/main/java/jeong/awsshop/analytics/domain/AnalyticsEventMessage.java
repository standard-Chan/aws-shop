package jeong.awsshop.analytics.domain;

import java.time.Instant;

public record AnalyticsEventMessage(
        Long eventId,
        AnalyticsEventType eventType,
        Long userId,
        Instant occurredAt,
        String keyword,
        Long productId,
        Long orderId
) {
}
