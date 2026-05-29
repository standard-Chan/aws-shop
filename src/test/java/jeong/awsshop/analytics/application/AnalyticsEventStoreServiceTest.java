package jeong.awsshop.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventRepository;
import jeong.awsshop.analytics.domain.AnalyticsStoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AnalyticsEventStoreServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private AnalyticsEventStoreService analyticsEventStoreService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-05-29T04:00:00Z"), ZoneOffset.UTC);
        analyticsEventStoreService = new AnalyticsEventStoreService(analyticsEventRepository, clock);
    }

    @Test
    @DisplayName("새 analytics 이벤트는 저장 시각을 채워 저장해야 한다")
    void should_save_new_event_with_created_at() {
        AnalyticsEventMessage message = AnalyticsEventMessage.search(
                1L,
                10L,
                Instant.parse("2026-05-29T03:00:00Z"),
                "macbook"
        );
        when(analyticsEventRepository.existsById(1L)).thenReturn(false);

        analyticsEventStoreService.saveIfAbsent(message);

        ArgumentCaptor<AnalyticsStoredEvent> eventCaptor = ArgumentCaptor.forClass(AnalyticsStoredEvent.class);
        verify(analyticsEventRepository).save(eventCaptor.capture());

        AnalyticsStoredEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventId()).isEqualTo(1L);
        assertThat(savedEvent.getUserId()).isEqualTo(10L);
        assertThat(savedEvent.getKeyword()).isEqualTo("macbook");
        assertThat(savedEvent.getProductId()).isNull();
        assertThat(savedEvent.getOrderId()).isNull();
        assertThat(savedEvent.getCreatedAt())
                .isEqualTo(Instant.parse("2026-05-29T04:00:00Z"));
    }

    @Test
    @DisplayName("이미 저장된 eventId는 다시 저장하지 않아야 한다")
    void should_skip_duplicate_event_id() {
        AnalyticsEventMessage message = AnalyticsEventMessage.purchase(
                2L,
                10L,
                Instant.parse("2026-05-29T03:00:00Z"),
                500L
        );
        when(analyticsEventRepository.existsById(2L)).thenReturn(true);

        analyticsEventStoreService.saveIfAbsent(message);

        verify(analyticsEventRepository, never()).save(org.mockito.Mockito.any(AnalyticsStoredEvent.class));
    }
}
