package jeong.awsshop.eventpipeline.productranking.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 상품 랭킹 조회 결과를 JVM 메모리에 저장한다.
 *
 * <p>Redis 저장소는 window bucket을 매번 합산해야 하므로 조회 요청마다 호출하면 같은 랭킹을 반복 계산하게 된다.
 * 이 컴포넌트는 window별 Top100 snapshot을 주기적으로 갱신하고, 요청 경로에서는 snapshot을 잘라 즉시 반환한다.
 *
 * <p>Redis가 일시적으로 사용할 수 없으면 기존 snapshot을 유지한다. 초기 snapshot도 없으면 빈 목록을 반환하며,
 * 실패 로그는 최초 1회만 남기고 이후 성공 복구 로그도 최초 1회만 남긴다.
 */
@Component
public class ProductRankingCache {

    private static final Logger log = LoggerFactory.getLogger(ProductRankingCache.class);
    private static final int CACHE_LIMIT = 100;

    private final ProductRankingStore productRankingStore;
    private final Clock clock;
    private final long refreshIntervalMillis;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final AtomicBoolean initialFailureLogged = new AtomicBoolean(false);
    private final AtomicBoolean recoveryLogged = new AtomicBoolean(false);
    private volatile Map<RankingWindow, List<ProductRankingItem>> rankings = emptyRankings();

    @Autowired
    public ProductRankingCache(
            ProductRankingStore productRankingStore,
            Clock clock,
            @Value("${event-pipeline.product-ranking.cache.refresh-interval-millis:1000}") long refreshIntervalMillis
    ) {
        this(
                productRankingStore,
                clock,
                refreshIntervalMillis,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "product-ranking-cache-refresh");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    ProductRankingCache(
            ProductRankingStore productRankingStore,
            Clock clock,
            long refreshIntervalMillis,
            ScheduledExecutorService executor
    ) {
        if (refreshIntervalMillis <= 0) {
            throw new IllegalArgumentException("refreshIntervalMillis must be positive");
        }
        this.productRankingStore = productRankingStore;
        this.clock = clock;
        this.refreshIntervalMillis = refreshIntervalMillis;
        this.executor = executor;
    }

    /**
     * 캐시 warm-up을 1회 시도하고, 이후 고정 지연 주기로 snapshot 갱신 작업을 시작한다.
     *
     * <p>warm-up 실패는 애플리케이션 기동을 막지 않는다. Redis가 아직 준비되지 않은 배포 상황에서도 서버는 뜨고,
     * background refresh가 계속 재시도하면서 Redis가 준비되면 캐시를 채운다.
     */
    @PostConstruct
    public void start() {
        refreshSafely();
        executor.scheduleWithFixedDelay(
                this::refreshSafely,
                refreshIntervalMillis,
                refreshIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 캐시 갱신 executor를 종료한다.
     */
    @PreDestroy
    public void stop() {
        executor.shutdown();
    }

    /**
     * 캐시된 window별 Top100 snapshot에서 요청 limit만큼 잘라 반환한다.
     *
     * @param window 조회할 랭킹 시간 범위
     * @param limit 반환할 최대 상품 수
     * @return 캐시된 상품 랭킹 목록. 아직 snapshot이 없거나 limit이 0 이하이면 빈 목록
     */
    public List<ProductRankingItem> findTop(RankingWindow window, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<ProductRankingItem> cachedRankings = rankings.getOrDefault(window, List.of());
        return cachedRankings.stream()
                .limit(limit)
                .toList();
    }

    /**
     * Redis 기반 snapshot 갱신을 안전하게 수행한다.
     *
     * 동시에 여러 갱신이 겹치면 하나만 실행한다.
     * 갱신 중 예외가 발생하면 기존 snapshot은 유지하고, 최초 실패 로그만 남긴 뒤 다음 주기에서 다시 시도한다.
     */
    void refreshSafely() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            refresh();
        } catch (RuntimeException exception) {
            if (initialFailureLogged.compareAndSet(false, true)) {
                log.warn("상품 랭킹 캐시 갱신을 위한 Redis 연동에 실패했습니다. 이후에도 캐시 갱신은 계속 재시도합니다.", exception);
            }
        } finally {
            refreshing.set(false);
        }
    }

    /**
     * 모든 RankingWindow의 Top100을 Redis 저장소에서 다시 계산해 snapshot을 원자적으로 교체한다.
     *
     * TODO : 1시간 이내의 데이터는 Redis로, 그 이상의 데이터는 ClickHouse로 조회처리할 예정이다.
     */
    private void refresh() {
        var now = clock.instant();
        Map<RankingWindow, List<ProductRankingItem>> refreshedRankings = new EnumMap<>(RankingWindow.class);
        for (RankingWindow window : RankingWindow.values()) {
            refreshedRankings.put(window, List.copyOf(productRankingStore.findTop(window, CACHE_LIMIT, now)));
        }
        rankings = refreshedRankings;

        if (initialFailureLogged.get() && recoveryLogged.compareAndSet(false, true)) {
            log.info("Redis 연동이 복구되어 상품 랭킹 캐시 갱신에 성공했습니다.");
        }
    }

    /**
     * 각 RankingWindow에 대해 빈 목록을 가진 초기 snapshot을 만든다.
     */
    private static Map<RankingWindow, List<ProductRankingItem>> emptyRankings() {
        Map<RankingWindow, List<ProductRankingItem>> emptyRankings = new EnumMap<>(RankingWindow.class);
        Arrays.stream(RankingWindow.values())
                .forEach(window -> emptyRankings.put(window, List.of()));
        return emptyRankings;
    }
}
