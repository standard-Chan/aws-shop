package jeong.awsshop.eventpipeline.producer.controller.dto;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;

public record EventAcceptedResponse(
        Long eventId,
        UserBehaviorEventType eventType
) {

    static public EventAcceptedResponse from(UserBehaviorEventMessage message) {
        return new EventAcceptedResponse(message.eventId(), message.eventType());
    }
}
