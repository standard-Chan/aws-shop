package jeong.awsshop.eventpipeline.productranking.application;

import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import org.springframework.stereotype.Service;

@Service
public class ProductRankingService {

    private static final long SEARCH_SCORE = 1L;
    private static final long PRODUCT_VIEW_SCORE = 3L;
    private static final long ADD_TO_CART_SCORE = 10L;
    // PURCHASE 이벤트는 현재 productId가 null로 들어오므로 주문 상품 라인 모델링 후 반영한다.
    // private static final long PURCHASE_SCORE = 30L;

    private final ProductRankingStore productRankingStore;

    public ProductRankingService(ProductRankingStore productRankingStore) {
        this.productRankingStore = productRankingStore;
    }

    public void record(UserBehaviorEventMessage event) {
        Long productId = event.productId();
        if (productId == null) {
            return;
        }

        long score = switch (event.eventType()) {
            case SEARCH -> SEARCH_SCORE;
            case PRODUCT_VIEW -> PRODUCT_VIEW_SCORE;
            case ADD_TO_CART -> ADD_TO_CART_SCORE;
            case PURCHASE -> {
                // 구매 이벤트는 현재 productId가 null이므로 우선 랭킹에 반영하지 않는다.
                // yield PURCHASE_SCORE;
                yield 0L;
            }
        };

        if (score > 0) {
            productRankingStore.increaseScore(productId, score);
        }
    }

    public List<ProductRankingItem> findTop(int limit) {
        return productRankingStore.findTop(limit);
    }
}
