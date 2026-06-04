package jeong.awsshop.eventpipeline.common;

import java.time.Instant;

public record UserBehaviorEventMessage(
        Long eventId,
        UserBehaviorEventType eventType,
        Long userId,
        Instant occurredAt,
        String keyword,
        Long productId,
        Long orderId,
        Long searchEventId
) {

    public static UserBehaviorEventMessage newMessage(
            Long eventId,
            UserBehaviorEventType eventType,
            Long userId,
            Instant occurredAt,
            String keyword,
            Long productId,
            Long orderId,
            Long searchEventId
    ) {
        return new UserBehaviorEventMessage(
                eventId,
                eventType,
                userId,
                occurredAt,
                keyword,
                productId,
                orderId,
                searchEventId
        );
    }
}
