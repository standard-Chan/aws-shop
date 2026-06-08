package jeong.awsshop.eventpipeline.productranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.productranking.infrastructure.InMemoryProductRankingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRankingServiceTest {

    private final ProductRankingService productRankingService =
            new ProductRankingService(new InMemoryProductRankingStore());

    @Test
    @DisplayName("이벤트 타입별 점수를 productId 기준으로 누적해야 한다")
    void should_accumulate_product_scores_by_event_type() {
        productRankingService.record(event(UserBehaviorEventType.SEARCH, 100L));
        productRankingService.record(event(UserBehaviorEventType.PRODUCT_VIEW, 100L));
        productRankingService.record(event(UserBehaviorEventType.ADD_TO_CART, 100L));
        productRankingService.record(event(UserBehaviorEventType.PRODUCT_VIEW, 200L));

        var rankings = productRankingService.findTop(10);

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
        productRankingService.record(event(UserBehaviorEventType.SEARCH, null));
        productRankingService.record(event(UserBehaviorEventType.PURCHASE, null));

        assertThat(productRankingService.findTop(10)).isEmpty();
    }

    private UserBehaviorEventMessage event(UserBehaviorEventType eventType, Long productId) {
        return UserBehaviorEventMessage.newMessage(
                1L,
                eventType,
                10L,
                Instant.parse("2026-06-07T06:00:00Z"),
                null,
                productId,
                null,
                null
        );
    }
}
