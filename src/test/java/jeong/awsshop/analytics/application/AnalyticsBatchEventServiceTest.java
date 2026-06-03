package jeong.awsshop.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventRequestItem;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventResponse;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

class AnalyticsBatchEventServiceTest {

    @Mock
    private KafkaTemplate<String, AnalyticsEventMessage> kafkaTemplate;

    private AnalyticsBatchEventService analyticsBatchEventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        Clock clock = Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneOffset.UTC);
        analyticsBatchEventService = new AnalyticsBatchEventService(
                new SnowflakeIdGenerator(1L),
                kafkaTemplate,
                clock,
                "search-events",
                "product-view-events",
                "cart-events",
                "purchase-events"
        );
    }

    @Test
    @DisplayName("batch 요청의 각 이벤트는 타입별 topic으로 Kafka에 발행되어야 한다")
    void should_publish_batch_events_to_topics() {
        AnalyticsBatchEventResponse response = analyticsBatchEventService.recordBatch(
                new AnalyticsBatchEventRequest(List.of(
                        new AnalyticsBatchEventRequestItem(AnalyticsEventType.SEARCH, 1L, "macbook", null, null, null),
                        new AnalyticsBatchEventRequestItem(AnalyticsEventType.PRODUCT_VIEW, 1L, "macbook", 100L, null, 10L),
                        new AnalyticsBatchEventRequestItem(AnalyticsEventType.ADD_TO_CART, 1L, null, 100L, null, null),
                        new AnalyticsBatchEventRequestItem(AnalyticsEventType.PURCHASE, 1L, null, null, 500L, null)
                ))
        );

        assertThat(response.acceptedCount()).isEqualTo(4);
        ArgumentCaptor<AnalyticsEventMessage> eventCaptor = ArgumentCaptor.forClass(AnalyticsEventMessage.class);
        verify(kafkaTemplate).send(eq("search-events"), eq("1"), eventCaptor.capture());
        verify(kafkaTemplate).send(eq("product-view-events"), eq("1"), eventCaptor.capture());
        verify(kafkaTemplate).send(eq("cart-events"), eq("1"), eventCaptor.capture());
        verify(kafkaTemplate).send(eq("purchase-events"), eq("1"), eventCaptor.capture());

        List<AnalyticsEventMessage> events = eventCaptor.getAllValues();
        assertThat(events)
                .extracting(AnalyticsEventMessage::eventType)
                .containsExactly(
                        AnalyticsEventType.SEARCH,
                        AnalyticsEventType.PRODUCT_VIEW,
                        AnalyticsEventType.ADD_TO_CART,
                        AnalyticsEventType.PURCHASE
                );
        assertThat(events)
                .extracting(AnalyticsEventMessage::occurredAt)
                .containsOnly(Instant.parse("2026-05-29T03:00:00Z"));
        assertThat(events.get(1).searchEventId()).isEqualTo(10L);
        assertThat(events.get(1).keyword()).isEqualTo("macbook");
    }
}
