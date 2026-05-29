package jeong.awsshop.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
}
