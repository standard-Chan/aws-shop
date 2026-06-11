package jeong.awsshop.eventpipeline.productranking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingMemoryStats;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.springframework.stereotype.Service;

/**
 * 사용자 행동 이벤트를 상품 랭킹 점수로 변환하고, 캐시된 랭킹 조회를 제공하는 application service다.
 *
 * 쓰기 경로는 {@link ProductRankingScoreWriter}로 전달해 batch/pipeline 저장 흐름을 타고,
 * 읽기 경로는 {@link ProductRankingCache}에서 주기적으로 갱신된 snapshot을 반환한다.
 */
@Service
public class ProductRankingService {

    private static final long SEARCH_SCORE = 1L;
    private static final long PRODUCT_VIEW_SCORE = 3L;
    private static final long ADD_TO_CART_SCORE = 10L;
    // PURCHASE 이벤트는 현재 productId가 null로 들어오므로 주문 상품 라인 모델링 후 반영한다.
    // private static final long PURCHASE_SCORE = 30L;

    private final ProductRankingStore productRankingStore;
    private final ProductRankingCache productRankingCache;
    private final ProductRankingScoreWriter productRankingScoreWriter;
    private final Clock clock;
    private final LongAdder processedEventCount = new LongAdder();

    public ProductRankingService(
            ProductRankingStore productRankingStore,
            ProductRankingCache productRankingCache,
            ProductRankingScoreWriter productRankingScoreWriter,
            Clock clock
    ) {
        this.productRankingStore = productRankingStore;
        this.productRankingCache = productRankingCache;
        this.productRankingScoreWriter = productRankingScoreWriter;
        this.clock = clock;
    }

    /**
     * 사용자 행동 이벤트를 이벤트 타입별 점수로 변환해 상품 랭킹에 반영한다.
     *
     * @param event 랭킹 점수로 변환할 사용자 행동 이벤트
     */
    public void record(UserBehaviorEventMessage event) {
        processedEventCount.increment();

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
            // Redis에 바로 쓰지 않고 buffer에 넘겨 batch/pipeline 쓰기 경로를 탄다.
            productRankingScoreWriter.save(new ProductRankingScoreDelta(productId, score, occurredAt(event)));
        }
    }

    /**
     * 지정한 시간 window의 상품 랭킹을 캐시에서 조회한다.
     *
     * @param window 조회할 랭킹 시간 범위
     * @param limit 반환할 최대 상품 수
     * @return 캐시된 상품 랭킹 목록
     */
    public List<ProductRankingItem> findTop(RankingWindow window, int limit) {
        return productRankingCache.findTop(window, limit);
    }

    /** 서버 집계 용도
     * 현재 application instance가 수신한 이벤트 수를 반환한다.
     */
    public long processedEventCount() {
        return processedEventCount.sum();
    }

    /** 서버 집계 용도
     * 랭킹 저장소와 JVM 메모리 사용량 통계를 반환한다.
     */
    public ProductRankingMemoryStats memoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        return new ProductRankingMemoryStats(
                productRankingStore.hashLength(),
                productRankingStore.estimatedHashMemoryBytes(),
                productRankingStore.estimatedBytesPerEntry(),
                productRankingStore.redisUsedMemoryBytes(),
                totalMemory - freeMemory,
                totalMemory,
                runtime.maxMemory()
        );
    }

    private Instant occurredAt(UserBehaviorEventMessage event) {
        if (event.occurredAt() == null) {
            return clock.instant();
        }
        return event.occurredAt();
    }
}
