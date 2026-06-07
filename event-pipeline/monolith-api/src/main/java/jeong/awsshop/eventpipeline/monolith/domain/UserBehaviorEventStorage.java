package jeong.awsshop.eventpipeline.monolith.domain;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;

public interface UserBehaviorEventStorage {

    void save(UserBehaviorEventMessage event);
}
