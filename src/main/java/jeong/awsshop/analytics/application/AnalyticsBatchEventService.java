package jeong.awsshop.analytics.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.exception.AnalyticsEventPublishException;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventRequestItem;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventResponse;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsBatchEventService {

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final KafkaTemplate<String, AnalyticsEventMessage> kafkaTemplate;
    private final Clock clock;
    private final String searchTopic;
    private final String productViewTopic;
    private final String cartTopic;
    private final String purchaseTopic;

    public AnalyticsBatchEventService(
            SnowflakeIdGenerator snowflakeIdGenerator,
            KafkaTemplate<String, AnalyticsEventMessage> kafkaTemplate,
            Clock clock,
            @Value("${app.analytics.kafka.topics.search:search-events}") String searchTopic,
            @Value("${app.analytics.kafka.topics.product-view:product-view-events}") String productViewTopic,
            @Value("${app.analytics.kafka.topics.cart:cart-events}") String cartTopic,
            @Value("${app.analytics.kafka.topics.purchase:purchase-events}") String purchaseTopic
    ) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.searchTopic = searchTopic;
        this.productViewTopic = productViewTopic;
        this.cartTopic = cartTopic;
        this.purchaseTopic = purchaseTopic;
    }

    public AnalyticsBatchEventResponse recordBatch(AnalyticsBatchEventRequest request) {
        List<AnalyticsEventMessage> events = request.events().stream()
                .map(this::toMessage)
                .toList();

        List<CompletableFuture<SendResult<String, AnalyticsEventMessage>>> futures = events.stream()
                .map(event -> kafkaTemplate.send(topicOf(event.eventType()), String.valueOf(event.userId()), event))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException exception) {
            throw new AnalyticsEventPublishException(
                    "[Analytics] batch 이벤트 발행에 실패했습니다. count=" + events.size(),
                    exception
            );
        }

        return new AnalyticsBatchEventResponse(events.size());
    }

    private AnalyticsEventMessage toMessage(AnalyticsBatchEventRequestItem item) {
        Instant occurredAt = Instant.now(clock);
        Long eventId = snowflakeIdGenerator.nextId();

        return switch (item.eventType()) {
            case SEARCH -> AnalyticsEventMessage.search(eventId, item.userId(), occurredAt, item.keyword());
            case PRODUCT_VIEW -> AnalyticsEventMessage.productView(
                    eventId,
                    item.userId(),
                    occurredAt,
                    item.productId(),
                    item.searchEventId(),
                    item.keyword()
            );
            case ADD_TO_CART -> AnalyticsEventMessage.addToCart(eventId, item.userId(), occurredAt, item.productId());
            case PURCHASE -> AnalyticsEventMessage.purchase(eventId, item.userId(), occurredAt, item.orderId());
        };
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
