package jeong.awsshop.eventpipeline.productranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import jeong.awsshop.eventpipeline.productranking.infrastructure.InMemoryProductRankingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRankingServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ProductRankingService productRankingService =
            new ProductRankingService(new InMemoryProductRankingStore(), CLOCK);

    @Test
    @DisplayName("이벤트 타입별 점수를 productId 기준으로 누적해야 한다")
    void should_accumulate_product_scores_by_event_type() {
        productRankingService.record(event(UserBehaviorEventType.SEARCH, 100L, NOW));
        productRankingService.record(event(UserBehaviorEventType.PRODUCT_VIEW, 100L, NOW));
        productRankingService.record(event(UserBehaviorEventType.ADD_TO_CART, 100L, NOW));
        productRankingService.record(event(UserBehaviorEventType.PRODUCT_VIEW, 200L, NOW));

        var rankings = productRankingService.findTop(RankingWindow.ONE_HOUR, 10);

        assertThat(rankings)
                .extracting("rank", "productId", "score")
                .containsExactly(
                        tuple(1L, 100L, 14L),
                        tuple(2L, 200L, 3L)
                );
    }

    @Test
    @DisplayName("productId가 없는 이벤트는 랭킹에 반영하지 않아야 한다")
    void should_ignore_event_without_product_id() {
        productRankingService.record(event(UserBehaviorEventType.SEARCH, null, NOW));
        productRankingService.record(event(UserBehaviorEventType.PURCHASE, null, NOW));

        assertThat(productRankingService.findTop(RankingWindow.ONE_HOUR, 10)).isEmpty();
    }

    @Test
    @DisplayName("1시간, 1일, 1주 윈도우별로 랭킹 집계 범위를 나눠야 한다")
    void should_find_rankings_by_time_window() {
        productRankingService.record(event(UserBehaviorEventType.ADD_TO_CART, 100L, NOW.minusSeconds(30 * 60)));
        productRankingService.record(event(UserBehaviorEventType.ADD_TO_CART, 200L, NOW.minusSeconds(2 * 60 * 60)));
        productRankingService.record(event(UserBehaviorEventType.ADD_TO_CART, 300L, NOW.minusSeconds(2 * 24 * 60 * 60)));
        productRankingService.record(event(UserBehaviorEventType.ADD_TO_CART, 400L, NOW.minusSeconds(8 * 24 * 60 * 60)));

        assertThat(productRankingService.findTop(RankingWindow.ONE_HOUR, 10))
                .extracting("productId", "score")
                .containsExactly(tuple(100L, 10L));
        assertThat(productRankingService.findTop(RankingWindow.ONE_DAY, 10))
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 10L),
                        tuple(200L, 10L)
                );
        assertThat(productRankingService.findTop(RankingWindow.ONE_WEEK, 10))
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 10L),
                        tuple(200L, 10L),
                        tuple(300L, 10L)
                );
    }

    private UserBehaviorEventMessage event(UserBehaviorEventType eventType, Long productId, Instant occurredAt) {
        return UserBehaviorEventMessage.newMessage(
                1L,
                eventType,
                10L,
                occurredAt,
                null,
                productId,
                null,
                null
        );
    }
}
