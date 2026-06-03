package jeong.awsshop.analytics.infrastructure;

import jeong.awsshop.analytics.application.AnalyticsEventStoreService;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Analytics Kafka topic을 구독하고 수신한 메시지를 저장 서비스로 넘기는 Consumer다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.analytics.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaAnalyticsEventConsumer {

    private final AnalyticsEventStoreService analyticsEventStoreService;

    /**
     * search-events topic에 검색 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.search:search-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}"
    )
    public void consumeSearch(AnalyticsEventMessage message) {
        analyticsEventStoreService.saveIfAbsent(message);
    }

    /**
     * product-view-events topic에 상품 조회 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.product-view:product-view-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}"
    )
    public void consumeProductView(AnalyticsEventMessage message) {
        analyticsEventStoreService.saveIfAbsent(message);
    }

    /**
     * cart-events topic에 장바구니 추가 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.cart:cart-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}"
    )
    public void consumeAddToCart(AnalyticsEventMessage message) {
        analyticsEventStoreService.saveIfAbsent(message);
    }

    /**
     * purchase-events topic에 구매 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.purchase:purchase-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}"
    )
    public void consumePurchase(AnalyticsEventMessage message) {
        analyticsEventStoreService.saveIfAbsent(message);
    }
}
