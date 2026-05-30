package jeong.awsshop.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.analytics.domain.AnalyticsEventRepository;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.domain.AnalyticsEventTypeCount;
import jeong.awsshop.analytics.domain.AnalyticsKeywordKpiCount;
import jeong.awsshop.analytics.domain.AnalyticsProductKpiCount;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKeywordKpiResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKpiSummaryResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsProductKpiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

class AnalyticsKpiServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private AnalyticsKpiService analyticsKpiService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        analyticsKpiService = new AnalyticsKpiService(analyticsEventRepository);
    }

    @Test
    @DisplayName("Summary KPI의 검색 CTR, 장바구니율, 구매율을 계산해야 한다")
    void should_calculate_summary_kpis() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsEventRepository.countByEventTypeBetween(from, to))
                .thenReturn(List.of(
                        count(AnalyticsEventType.SEARCH, 100L),
                        count(AnalyticsEventType.PRODUCT_VIEW, 40L),
                        count(AnalyticsEventType.ADD_TO_CART, 12L),
                        count(AnalyticsEventType.PURCHASE, 3L)
                ));

        AnalyticsKpiSummaryResponse response = analyticsKpiService.getSummary(from, to);

        assertThat(response.basis()).isEqualTo("EVENT_COUNT");
        assertThat(response.searchCount()).isEqualTo(100L);
        assertThat(response.productViewCount()).isEqualTo(40L);
        assertThat(response.addToCartCount()).isEqualTo(12L);
        assertThat(response.purchaseCount()).isEqualTo(3L);
        assertThat(response.searchCtr()).isEqualTo(0.4);
        assertThat(response.cartRate()).isEqualTo(0.3);
        assertThat(response.purchaseRate()).isEqualTo(0.075);
    }

    @Test
    @DisplayName("Summary KPI의 분모가 0이면 rate를 0으로 처리해야 한다")
    void should_return_zero_rate_when_summary_denominator_is_zero() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsEventRepository.countByEventTypeBetween(from, to))
                .thenReturn(List.of(count(AnalyticsEventType.ADD_TO_CART, 12L)));

        AnalyticsKpiSummaryResponse response = analyticsKpiService.getSummary(from, to);

        assertThat(response.searchCtr()).isZero();
        assertThat(response.cartRate()).isZero();
        assertThat(response.purchaseRate()).isZero();
    }

    @Test
    @DisplayName("Product KPI의 장바구니율을 계산하고 구매율은 null이어야 한다")
    void should_calculate_product_kpis() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsEventRepository.findProductKpiCounts(
                from,
                to,
                AnalyticsEventType.PRODUCT_VIEW,
                AnalyticsEventType.ADD_TO_CART,
                PageRequest.of(0, 20)
        )).thenReturn(List.of(productCount(100L, 40L, 12L)));

        AnalyticsProductKpiResponse response = analyticsKpiService.getProducts(from, to, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(100L);
        assertThat(response.items().get(0).cartRate()).isEqualTo(0.3);
        assertThat(response.items().get(0).purchaseRate()).isNull();
    }

    @Test
    @DisplayName("Keyword KPI는 검색 수와 조회 수 기준으로 CTR을 계산해야 한다")
    void should_calculate_keyword_kpis() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsEventRepository.findKeywordKpiCounts(
                from,
                to,
                AnalyticsEventType.SEARCH,
                AnalyticsEventType.PRODUCT_VIEW,
                PageRequest.of(0, 10)
        )).thenReturn(List.of(keywordCount("macbook", 100L, 30L)));

        AnalyticsKeywordKpiResponse response = analyticsKpiService.getKeywords(from, to, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).keyword()).isEqualTo("macbook");
        assertThat(response.items().get(0).searchCtr()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("from이 to보다 빠르지 않으면 400 예외를 던져야 한다")
    void should_reject_invalid_period() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> analyticsKpiService.getSummary(from, to))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    @DisplayName("limit이 1에서 100 사이가 아니면 400 예외를 던져야 한다")
    void should_reject_invalid_limit() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> analyticsKpiService.getProducts(from, to, 101))
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

    private AnalyticsProductKpiCount productCount(Long productId, long productViewCount, long addToCartCount) {
        return new AnalyticsProductKpiCount() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public long getProductViewCount() {
                return productViewCount;
            }

            @Override
            public long getAddToCartCount() {
                return addToCartCount;
            }
        };
    }

    private AnalyticsKeywordKpiCount keywordCount(String keyword, long searchCount, long productViewCount) {
        return new AnalyticsKeywordKpiCount() {
            @Override
            public String getKeyword() {
                return keyword;
            }

            @Override
            public long getSearchCount() {
                return searchCount;
            }

            @Override
            public long getProductViewCount() {
                return productViewCount;
            }
        };
    }
}
