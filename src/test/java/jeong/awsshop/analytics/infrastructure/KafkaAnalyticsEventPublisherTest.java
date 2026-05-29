package jeong.awsshop.analytics.infrastructure;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@SuppressWarnings("unchecked")
class KafkaAnalyticsEventPublisherTest {

    private final KafkaTemplate<String, AnalyticsEventMessage> kafkaTemplate =
            org.mockito.Mockito.mock(KafkaTemplate.class);

    private final KafkaAnalyticsEventPublisher publisher = new KafkaAnalyticsEventPublisher(
            kafkaTemplate,
            "search-events",
            "product-view-events",
            "cart-events",
            "purchase-events"
    );

    @Test
    @DisplayName("이벤트 타입별 topic과 userId key로 Kafka에 발행해야 한다")
    void should_publish_to_topic_by_event_type_with_user_id_key() {
        AnalyticsEventMessage event = AnalyticsEventMessage.productView(
                1L,
                9L,
                Instant.parse("2026-05-29T03:00:00Z"),
                100L
        );

        publisher.publish(event);

        verify(kafkaTemplate).send("product-view-events", "9", event);
    }
}
