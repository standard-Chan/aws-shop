package jeong.awsshop.analytics.infrastructure;

import jeong.awsshop.analytics.application.AnalyticsEventPublisher;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.analytics.kafka.enabled", havingValue = "false")
public class DisabledAnalyticsEventPublisher implements AnalyticsEventPublisher {

    @Override
    public void publish(AnalyticsEventMessage event) {
        log.debug("[Analytics] Kafka disabled. Skip event publish. eventType={}, eventId={}",
            event.eventType(), event.eventId());
    }
}
