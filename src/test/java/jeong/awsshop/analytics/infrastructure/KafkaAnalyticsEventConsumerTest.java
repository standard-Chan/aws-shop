package jeong.awsshop.analytics.infrastructure;

import static org.mockito.Mockito.verify;

import java.util.List;
import java.time.Instant;
import jeong.awsshop.analytics.application.AnalyticsEventStoreService;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KafkaAnalyticsEventConsumerTest {

    private final AnalyticsEventStoreService analyticsEventStoreService =
            org.mockito.Mockito.mock(AnalyticsEventStoreService.class);

    private final KafkaAnalyticsEventConsumer consumer =
            new KafkaAnalyticsEventConsumer(analyticsEventStoreService);

    @Test
    @DisplayName("검색 이벤트 listener는 저장 서비스에 메시지를 위임해야 한다")
    void should_delegate_search_event_to_store_service() {
        AnalyticsEventMessage message = AnalyticsEventMessage.search(
                1L,
                10L,
                Instant.parse("2026-05-29T03:00:00Z"),
                "macbook"
        );

        consumer.consumeSearch(List.of(message));

        verify(analyticsEventStoreService).saveAllIfAbsent(List.of(message));
    }

    @Test
    @DisplayName("상품 조회 이벤트 listener는 저장 서비스에 메시지를 위임해야 한다")
    void should_delegate_product_view_event_to_store_service() {
        AnalyticsEventMessage message = AnalyticsEventMessage.productView(
                2L,
                10L,
                Instant.parse("2026-05-29T03:00:00Z"),
                100L
        );

        consumer.consumeProductView(List.of(message));

        verify(analyticsEventStoreService).saveAllIfAbsent(List.of(message));
    }

    @Test
    @DisplayName("장바구니 이벤트 listener는 저장 서비스에 메시지를 위임해야 한다")
    void should_delegate_add_to_cart_event_to_store_service() {
        AnalyticsEventMessage message = AnalyticsEventMessage.addToCart(
                3L,
                10L,
                Instant.parse("2026-05-29T03:00:00Z"),
                100L
        );

        consumer.consumeAddToCart(List.of(message));

        verify(analyticsEventStoreService).saveAllIfAbsent(List.of(message));
    }

    @Test
    @DisplayName("구매 이벤트 listener는 저장 서비스에 메시지를 위임해야 한다")
    void should_delegate_purchase_event_to_store_service() {
        AnalyticsEventMessage message = AnalyticsEventMessage.purchase(
                4L,
                10L,
                Instant.parse("2026-05-29T03:00:00Z"),
                500L
        );

        consumer.consumePurchase(List.of(message));

        verify(analyticsEventStoreService).saveAllIfAbsent(List.of(message));
    }
}
