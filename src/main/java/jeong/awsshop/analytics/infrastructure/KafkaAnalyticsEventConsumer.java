package jeong.awsshop.analytics.infrastructure;

import java.util.List;
import jeong.awsshop.analytics.application.AnalyticsEventStoreService;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Analytics Kafka topic을 구독하고 수신한 메시지를 저장 서비스로 넘기는 Consumer다.
 */
@Component
@RequiredArgsConstructor
public class KafkaAnalyticsEventConsumer {

    private final AnalyticsEventStoreService analyticsEventStoreService;

    /**
     * search-events topic에 검색 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.search:search-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}",
            containerFactory = "analyticsKafkaBatchListenerContainerFactory"
    )
    public void consumeSearch(List<AnalyticsEventMessage> messages) {
        analyticsEventStoreService.saveAllIfAbsent(messages);
    }

    /**
     * product-view-events topic에 상품 조회 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.product-view:product-view-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}",
            containerFactory = "analyticsKafkaBatchListenerContainerFactory"
    )
    public void consumeProductView(List<AnalyticsEventMessage> messages) {
        analyticsEventStoreService.saveAllIfAbsent(messages);
    }

    /**
     * cart-events topic에 장바구니 추가 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.cart:cart-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}",
            containerFactory = "analyticsKafkaBatchListenerContainerFactory"
    )
    public void consumeAddToCart(List<AnalyticsEventMessage> messages) {
        analyticsEventStoreService.saveAllIfAbsent(messages);
    }

    /**
     * purchase-events topic에 구매 이벤트가 들어오면 호출된다.
     */
    @KafkaListener(
            topics = "${app.analytics.kafka.topics.purchase:purchase-events}",
            groupId = "${app.analytics.kafka.consumer.group-id:analytics-event-consumer-group}",
            containerFactory = "analyticsKafkaBatchListenerContainerFactory"
    )
    public void consumePurchase(List<AnalyticsEventMessage> messages) {
        analyticsEventStoreService.saveAllIfAbsent(messages);
    }
}
