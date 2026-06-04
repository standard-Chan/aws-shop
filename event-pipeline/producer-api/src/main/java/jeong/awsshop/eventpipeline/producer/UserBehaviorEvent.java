package jeong.awsshop.eventpipeline.producer;

import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;

public record UserBehaviorEvent (
    Long eventId,
    UserBehaviorEventType eventType,
    Long userId,
    Instant occurredAt,
    String keyword,
    Long productId,
    Long orderId,
    Long searchEventId
) {}
