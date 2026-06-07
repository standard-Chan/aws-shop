package jeong.awsshop.eventpipeline.monolith.infrastructure;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingUserBehaviorEventSink implements UserBehaviorEventSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingUserBehaviorEventSink.class);

    @Override
    public void save(UserBehaviorEventMessage event) {
        log.info(
                "Collected user behavior event. eventId={}, eventType={}, userId={}",
                event.eventId(),
                event.eventType(),
                event.userId()
        );
    }
}
