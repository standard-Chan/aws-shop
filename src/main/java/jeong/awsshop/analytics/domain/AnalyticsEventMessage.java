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

    public static AnalyticsEventMessage search(Long eventId, Long userId, Instant occurredAt, String keyword) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.SEARCH,
                userId,
                occurredAt,
                keyword,
                null,
                null
        );
    }

    public static AnalyticsEventMessage productView(Long eventId, Long userId, Instant occurredAt, Long productId) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.PRODUCT_VIEW,
                userId,
                occurredAt,
                null,
                productId,
                null
        );
    }

    public static AnalyticsEventMessage addToCart(Long eventId, Long userId, Instant occurredAt, Long productId) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.ADD_TO_CART,
                userId,
                occurredAt,
                null,
                productId,
                null
        );
    }

    public static AnalyticsEventMessage purchase(Long eventId, Long userId, Instant occurredAt, Long orderId) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.PURCHASE,
                userId,
                occurredAt,
                null,
                null,
                orderId
        );
    }
}
