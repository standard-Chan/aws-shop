package jeong.awsshop.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
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
        assertThat(savedEvent.getSearchEventId()).isNull();
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
                        Tuple.tuple(AnalyticsEventType.SEARCH, 2L),
                        Tuple.tuple(AnalyticsEventType.PRODUCT_VIEW, 1L)
                );
    }

    @Test
    @DisplayName("상품별 KPI는 기간 내 상품별 조회와 장바구니 수를 집계하고 정렬해야 한다")
    void should_find_product_kpi_counts() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        saveProductViewEvent(10L, from, 200L, null, null);
        saveProductViewEvent(11L, from, 100L, null, null);
        saveProductViewEvent(12L, from, 100L, null, null);
        saveAddToCartEvent(13L, from, 100L);
        saveAddToCartEvent(14L, from, 300L);
        saveProductViewEvent(15L, to, 100L, null, null);

        List<AnalyticsProductKpiCount> counts = analyticsEventRepository.findProductKpiCounts(
                from,
                to,
                AnalyticsEventType.PRODUCT_VIEW,
                AnalyticsEventType.ADD_TO_CART,
                PageRequest.of(0, 20)
        );

        assertThat(counts)
                .extracting(
                        AnalyticsProductKpiCount::getProductId,
                        AnalyticsProductKpiCount::getProductViewCount,
                        AnalyticsProductKpiCount::getAddToCartCount
                )
                .containsExactly(
                        Tuple.tuple(100L, 2L, 1L),
                        Tuple.tuple(200L, 1L, 0L)
                );
    }

    @Test
    @DisplayName("검색어별 KPI는 검색어별 검색과 상품 조회 수를 집계하고 정렬해야 한다")
    void should_find_keyword_kpi_counts() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        saveSearchEvent(20L, from, "monitor");
        saveSearchEvent(21L, from, "macbook");
        saveSearchEvent(22L, from, "macbook");
        saveProductViewEvent(23L, from, 100L, 21L, "macbook");
        saveProductViewEvent(24L, from, 200L, 20L, "monitor");
        saveProductViewEvent(25L, from, 300L, null, null);
        saveSearchEvent(26L, to, "macbook");

        List<AnalyticsKeywordKpiCount> counts = analyticsEventRepository.findKeywordKpiCounts(
                from,
                to,
                AnalyticsEventType.SEARCH,
                AnalyticsEventType.PRODUCT_VIEW,
                PageRequest.of(0, 20)
        );

        assertThat(counts)
                .extracting(
                        AnalyticsKeywordKpiCount::getKeyword,
                        AnalyticsKeywordKpiCount::getSearchCount,
                        AnalyticsKeywordKpiCount::getProductViewCount
                )
                .containsExactly(
                        Tuple.tuple("macbook", 2L, 1L),
                        Tuple.tuple("monitor", 1L, 1L)
                );
    }

    @Test
    @DisplayName("상품 조회 이벤트의 searchEventId를 저장해야 한다")
    void should_save_search_event_id_for_product_view() {
        AnalyticsEventMessage message = AnalyticsEventMessage.productView(
                30L,
                1L,
                Instant.parse("2026-05-01T00:00:00Z"),
                100L,
                20L,
                "macbook"
        );

        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                message,
                Instant.parse("2026-05-29T04:00:00Z")
        ));

        AnalyticsStoredEvent savedEvent = analyticsEventRepository.findById(30L).orElseThrow();
        assertThat(savedEvent.getSearchEventId()).isEqualTo(20L);
        assertThat(savedEvent.getKeyword()).isEqualTo("macbook");
    }

    private void saveSearchEvent(Long eventId, Instant occurredAt) {
        saveSearchEvent(eventId, occurredAt, "macbook");
    }

    private void saveSearchEvent(Long eventId, Instant occurredAt, String keyword) {
        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                AnalyticsEventMessage.search(eventId, 1L, occurredAt, keyword),
                Instant.parse("2026-05-29T04:00:00Z")
        ));
    }

    private void saveProductViewEvent(Long eventId, Instant occurredAt) {
        saveProductViewEvent(eventId, occurredAt, 100L, null, null);
    }

    private void saveProductViewEvent(
            Long eventId,
            Instant occurredAt,
            Long productId,
            Long searchEventId,
            String searchKeyword
    ) {
        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                AnalyticsEventMessage.productView(eventId, 1L, occurredAt, productId, searchEventId, searchKeyword),
                Instant.parse("2026-05-29T04:00:00Z")
        ));
    }

    private void saveAddToCartEvent(Long eventId, Instant occurredAt, Long productId) {
        analyticsEventRepository.save(AnalyticsStoredEvent.from(
                AnalyticsEventMessage.addToCart(eventId, 1L, occurredAt, productId),
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
