package jeong.awsshop.eventpipeline.dbconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.ConsumerFactory;

class EventDbConsumerKafkaConfigTest {

    private final EventDbConsumerKafkaConfig config = new EventDbConsumerKafkaConfig();

    @Test
    @DisplayName("Kafka JSON 역직렬화에서 Instant occurredAt을 읽어야 한다")
    void shouldDeserializeInstantOccurredAt() {
        ObjectMapper objectMapper = config.objectMapper();
        KafkaProperties kafkaProperties = new KafkaProperties();
        ConsumerFactory<String, UserBehaviorEventMessage> consumerFactory =
                config.userBehaviorEventConsumerFactory(kafkaProperties, objectMapper);

        UserBehaviorEventMessage message = consumerFactory.getValueDeserializer().deserialize(
                "search-events",
                """
                {
                  "eventId": 1,
                  "eventType": "SEARCH",
                  "userId": 10,
                  "occurredAt": "2026-06-05T05:22:58.600Z",
                  "keyword": "keyboard",
                  "productId": null,
                  "orderId": null,
                  "searchEventId": null
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-06-05T05:22:58.600Z"));
        assertThat(message.eventType()).isEqualTo(UserBehaviorEventType.SEARCH);
    }
}
