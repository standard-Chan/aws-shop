package jeong.awsshop.eventpipeline.monolith.infrastructure;

import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventSink;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import org.springframework.stereotype.Component;

@Component
public class CompositeUserBehaviorEventSink implements UserBehaviorEventSink {

    private final List<UserBehaviorEventStorage> storages;

    public CompositeUserBehaviorEventSink(List<UserBehaviorEventStorage> storages) {
        this.storages = storages;
    }

    @Override
    public void save(UserBehaviorEventMessage event) {
        for (UserBehaviorEventStorage storage : storages) {
            storage.save(event);
        }
    }
}
