package jeong.awsshop.eventpipeline.hadoopconsumer;

import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserBehaviorEventHadoopListener {

    private final UserBehaviorEventFileSink eventFileSink;

    public UserBehaviorEventHadoopListener(UserBehaviorEventFileSink eventFileSink) {
        this.eventFileSink = eventFileSink;
    }

    @KafkaListener(
        topics = {
            "${event-pipeline.topics.search:search-events}",
            "${event-pipeline.topics.product-view:product-view-events}",
            "${event-pipeline.topics.cart:cart-events}",
            "${event-pipeline.topics.purchase:purchase-events}"
        },
        groupId = "${event-pipeline.consumer.group-id:analytics-hadoop-consumer-group}",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(List<UserBehaviorEventMessage> messages) {

        if (messages.isEmpty()) {
            return;
        }

        eventFileSink.appendBatch(messages);
    }
}