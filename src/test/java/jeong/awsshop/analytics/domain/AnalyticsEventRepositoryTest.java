package jeong.awsshop.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsEventRepositoryTest {

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;

    @BeforeEach
    void setUp() {
        analyticsEventRepository.deleteAll();
    }

    @Test
    @DisplayName("analytics_events 테이블에 이벤트 메시지를 저장하고 조회해야 한다")
    void should_save_and_find_analytics_event() {
        AnalyticsEventMessage message = AnalyticsEventMessage.addToCart(
                100L,
                9L,
                Instant.parse("2026-05-29T03:00:00Z"),
                200L
        );
        AnalyticsStoredEvent event = AnalyticsStoredEvent.from(
                message,
                Instant.parse("2026-05-29T04:00:00Z")
        );

        analyticsEventRepository.save(event);

        AnalyticsStoredEvent savedEvent = analyticsEventRepository.findById(100L).orElseThrow();
        assertThat(savedEvent.getEventId()).isEqualTo(100L);
        assertThat(savedEvent.getEventType()).isEqualTo(AnalyticsEventType.ADD_TO_CART);
        assertThat(savedEvent.getUserId()).isEqualTo(9L);
        assertThat(savedEvent.getOccurredAt()).isEqualTo(Instant.parse("2026-05-29T03:00:00Z"));
        assertThat(savedEvent.getProductId()).isEqualTo(200L);
        assertThat(savedEvent.getKeyword()).isNull();
        assertThat(savedEvent.getOrderId()).isNull();
        assertThat(savedEvent.getCreatedAt()).isEqualTo(Instant.parse("2026-05-29T04:00:00Z"));
    }

    @Test
    @DisplayName("기간 내 이벤트를 eventType별로 집계하고 to 경계는 제외해야 한다")
    void should_count_events_by_type_in_period() {
        saveSearchEvent(1L, Instant.parse("2026-05-01T00:00:00Z"));
        saveSearchEvent(2L, Instant.parse("2026-05-15T00:00:00Z"));
        saveProductViewEvent(3L, Instant.parse("2026-05-20T00:00:00Z"));
        savePurchaseEvent(4L, Instant.parse("2026-06-01T00:00:00Z"));

        List<AnalyticsEventTypeCount> counts = analyticsEventRepository.countByEventTypeBetween(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );

        assertThat(counts)
                .extracting(AnalyticsEventTypeCount::getEventType, AnalyticsEventTypeCount::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(AnalyticsEventType.SEARCH, 2L),
                        org.assertj.core.groups.Tuple.tuple(AnalyticsEventType.PRODUCT_VIEW, 1L)
                );
    }

    private void saveSearchEvent(Long eventId, Instant occurredAt) {
        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                AnalyticsEventMessage.search(eventId, 1L, occurredAt, "macbook"),
                Instant.parse("2026-05-29T04:00:00Z")
        ));
    }

    private void saveProductViewEvent(Long eventId, Instant occurredAt) {
        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                AnalyticsEventMessage.productView(eventId, 1L, occurredAt, 100L),
                Instant.parse("2026-05-29T04:00:00Z")
        ));
    }

    private void savePurchaseEvent(Long eventId, Instant occurredAt) {
        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                AnalyticsEventMessage.purchase(eventId, 1L, occurredAt, 500L),
                Instant.parse("2026-05-29T04:00:00Z")
        ));
    }
}
