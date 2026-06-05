package jeong.awsshop.eventpipeline.hadoopconsumer;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserBehaviorEventHadoopListener {

    // 실제 파일 append 책임은 별도 Sink 로 위임해 Kafka 수신 로직과 파일 I/O 정책을 분리한다.
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
            groupId = "${event-pipeline.consumer.group-id:analytics-hadoop-consumer-group}"
    )
    // 사용자 행동 이벤트 4종을 같은 Hadoop staging 파일에 누적해 후속 배치 적재 입력으로 사용한다.
    public void consume(UserBehaviorEventMessage message) {
        eventFileSink.append(message);
    }
}
