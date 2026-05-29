package jeong.awsshop.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.analytics.domain.AnalyticsEventRepository;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.domain.AnalyticsEventTypeCount;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelStepResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

class AnalyticsFunnelServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private AnalyticsFunnelService analyticsFunnelService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        analyticsFunnelService = new AnalyticsFunnelService(analyticsEventRepository);
    }

    @Test
    @DisplayName("이벤트 수 기반 퍼널 단계와 전환율을 계산해야 한다")
    void should_calculate_event_count_funnel() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsEventRepository.countByEventTypeBetween(from, to))
                .thenReturn(List.of(
                        count(AnalyticsEventType.SEARCH, 100L),
                        count(AnalyticsEventType.PRODUCT_VIEW, 40L),
                        count(AnalyticsEventType.ADD_TO_CART, 12L),
                        count(AnalyticsEventType.PURCHASE, 3L)
                ));

        AnalyticsFunnelResponse response = analyticsFunnelService.getFunnel(from, to);

        assertThat(response.from()).isEqualTo(from);
        assertThat(response.to()).isEqualTo(to);
        assertThat(response.basis()).isEqualTo("EVENT_COUNT");
        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::eventType)
                .containsExactly(
                        AnalyticsEventType.SEARCH,
                        AnalyticsEventType.PRODUCT_VIEW,
                        AnalyticsEventType.ADD_TO_CART,
                        AnalyticsEventType.PURCHASE
                );
        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::count)
                .containsExactly(100L, 40L, 12L, 3L);
        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::conversionRateFromPrevious)
                .containsExactly(null, 0.4, 0.3, 0.25);
        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::conversionRateFromSearch)
                .containsExactly(1.0, 0.4, 0.12, 0.03);
    }

    @Test
    @DisplayName("조회 결과가 없는 단계는 count 0과 전환율 0으로 채워야 한다")
    void should_fill_missing_steps_with_zero() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsEventRepository.countByEventTypeBetween(from, to))
                .thenReturn(List.of(count(AnalyticsEventType.PRODUCT_VIEW, 5L)));

        AnalyticsFunnelResponse response = analyticsFunnelService.getFunnel(from, to);

        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::count)
                .containsExactly(0L, 5L, 0L, 0L);
        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::conversionRateFromPrevious)
                .containsExactly(null, 0.0, 0.0, 0.0);
        assertThat(response.steps())
                .extracting(AnalyticsFunnelStepResponse::conversionRateFromSearch)
                .containsExactly(0.0, 0.0, 0.0, 0.0);
    }

    @Test
    @DisplayName("from이 to보다 빠르지 않으면 400 예외를 던져야 한다")
    void should_reject_invalid_period() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> analyticsFunnelService.getFunnel(from, to))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private AnalyticsEventTypeCount count(AnalyticsEventType eventType, long count) {
        return new AnalyticsEventTypeCount() {
            @Override
            public AnalyticsEventType getEventType() {
                return eventType;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
