package jeong.awsshop.eventpipeline.monolith.presentation.dto;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;

public record EventAcceptedResponse(
        Long eventId,
        UserBehaviorEventType eventType
) {

    public static EventAcceptedResponse from(UserBehaviorEventMessage event) {
        return new EventAcceptedResponse(event.eventId(), event.eventType());
    }
}
