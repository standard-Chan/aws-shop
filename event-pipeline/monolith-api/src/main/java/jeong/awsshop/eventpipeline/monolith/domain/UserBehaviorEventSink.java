package jeong.awsshop.eventpipeline.monolith.domain;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;

public interface UserBehaviorEventSink {

    void save(UserBehaviorEventMessage event);
}
