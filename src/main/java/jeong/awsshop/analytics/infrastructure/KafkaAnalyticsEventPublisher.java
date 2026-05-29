package jeong.awsshop.analytics.infrastructure;

import java.util.concurrent.CompletableFuture;
import jeong.awsshop.analytics.application.AnalyticsEventPublisher;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.exception.AnalyticsEventPublishException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class KafkaAnalyticsEventPublisher implements AnalyticsEventPublisher {

    private final KafkaTemplate<String, AnalyticsEventMessage> kafkaTemplate;
    private final String searchTopic;
    private final String productViewTopic;
    private final String cartTopic;
    private final String purchaseTopic;

    public KafkaAnalyticsEventPublisher(
            KafkaTemplate<String, AnalyticsEventMessage> kafkaTemplate,
            @Value("${app.analytics.kafka.topics.search:search-events}") String searchTopic,
            @Value("${app.analytics.kafka.topics.product-view:product-view-events}") String productViewTopic,
            @Value("${app.analytics.kafka.topics.cart:cart-events}") String cartTopic,
            @Value("${app.analytics.kafka.topics.purchase:purchase-events}") String purchaseTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.searchTopic = searchTopic;
        this.productViewTopic = productViewTopic;
        this.cartTopic = cartTopic;
        this.purchaseTopic = purchaseTopic;
    }

    @Override
    public void publish(AnalyticsEventMessage event) {
        try {
            CompletableFuture<SendResult<String, AnalyticsEventMessage>> future =
                    kafkaTemplate.send(topicOf(event.eventType()), String.valueOf(event.userId()), event);
            if (future != null) {
                future.join();
            }
        } catch (RuntimeException exception) {
            throw new AnalyticsEventPublishException(
                    "[Analytics] 이벤트 발행에 실패했습니다. eventType=" + event.eventType()
                            + ", eventId=" + event.eventId(),
                    exception
            );
        }
    }

    private String topicOf(AnalyticsEventType eventType) {
        return switch (eventType) {
            case SEARCH -> searchTopic;
            case PRODUCT_VIEW -> productViewTopic;
            case ADD_TO_CART -> cartTopic;
            case PURCHASE -> purchaseTopic;
        };
    }
}
