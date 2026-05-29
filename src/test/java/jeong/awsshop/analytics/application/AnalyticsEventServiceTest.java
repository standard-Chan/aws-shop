package jeong.awsshop.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.presentation.dto.SearchEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsEventResponse;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AnalyticsEventServiceTest {

    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    private AnalyticsEventService analyticsEventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneOffset.UTC);
        analyticsEventService = new AnalyticsEventService(
                new SnowflakeIdGenerator(1L),
                analyticsEventPublisher,
                clock
        );
    }

    @Test
    @DisplayName("검색 이벤트는 서버 eventId와 occurredAt을 채워 publisher에 전달해야 한다")
    void should_publish_search_event_with_server_generated_fields() {
        AnalyticsEventResponse response = analyticsEventService.recordSearch(
                new SearchEventRequest(1L, "macbook")
        );

        ArgumentCaptor<AnalyticsEventMessage> eventCaptor = ArgumentCaptor.forClass(AnalyticsEventMessage.class);
        verify(analyticsEventPublisher).publish(eventCaptor.capture());

        AnalyticsEventMessage event = eventCaptor.getValue();
        assertThat(response.eventId()).isEqualTo(event.eventId());
        assertThat(response.eventType()).isEqualTo(AnalyticsEventType.SEARCH);
        assertThat(event.eventType()).isEqualTo(AnalyticsEventType.SEARCH);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-29T03:00:00Z"));
        assertThat(event.keyword()).isEqualTo("macbook");
        assertThat(event.productId()).isNull();
        assertThat(event.orderId()).isNull();
    }
}
