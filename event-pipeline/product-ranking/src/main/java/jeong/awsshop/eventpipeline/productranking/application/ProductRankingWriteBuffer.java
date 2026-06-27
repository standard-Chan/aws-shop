package jeong.awsshop.eventpipeline.productranking.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.infrastructure.clickhouse.ClickHouseProductRankingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductRankingWriteBuffer implements ProductRankingScoreWriter {

    private static final Logger log = LoggerFactory.getLogger(ProductRankingWriteBuffer.class);
    private static final long QUEUE_SIZE_LOG_INTERVAL_MILLIS = 5000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /*
     * HTTP 요청 thread와 Redis write thread 사이의 경계다.
     * 요청 thread는 queue 적재까지만 수행하고, 실제 Redis I/O는 background worker가 batch 단위로 처리한다.
     */
    private final BlockingQueue<ProductRankingScoreDelta> queue;

    /*
     * batch size 도달 flush와 주기 flush가 겹쳐도 queue drain은 한 번에 하나만 수행한다.
     */
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final int batchSize;
    private final long flushIntervalMillis;
    private final ProductRankingStore productRankingStore;
    // ClickHouse는 선택 기능이다. Bean이 없으면 Redis-only 경로로 동작한다.
    private final ClickHouseProductRankingStore clickHouseProductRankingStore;
    private final ProductRankingScoreCompressor productRankingScoreCompressor = new ProductRankingScoreCompressor();
    private final ScheduledExecutorService executor;

    @Autowired
    public ProductRankingWriteBuffer(
            @Value("${event-pipeline.product-ranking.batch.size:5000}") int batchSize,
            @Value("${event-pipeline.product-ranking.batch.flush-interval-millis:500}") long flushIntervalMillis,
            @Value("${event-pipeline.product-ranking.batch.queue-capacity:100000}") int queueCapacity,
            ProductRankingStore productRankingStore,
            ObjectProvider<ClickHouseProductRankingStore> clickHouseProductRankingStoreProvider
    ) {
        this(
                batchSize,
                flushIntervalMillis,
                queueCapacity,
                productRankingStore,
                clickHouseProductRankingStoreProvider.getIfAvailable(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "product-ranking-batch-writer");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    ProductRankingWriteBuffer(
            int batchSize,
            long flushIntervalMillis,
            int queueCapacity,
            ProductRankingStore productRankingStore,
            ScheduledExecutorService executor
    ) {
        this(batchSize, flushIntervalMillis, queueCapacity, productRankingStore, null, executor);
    }

    ProductRankingWriteBuffer(
            int batchSize,
            long flushIntervalMillis,
            int queueCapacity,
            ProductRankingStore productRankingStore,
            ClickHouseProductRankingStore clickHouseProductRankingStore,
            ScheduledExecutorService executor
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (flushIntervalMillis <= 0) {
            throw new IllegalArgumentException("flushIntervalMillis must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.productRankingStore = productRankingStore;
        this.clickHouseProductRankingStore = clickHouseProductRankingStore;
        this.executor = executor;
    }

    @PostConstruct
    public void start() {
        // 트래픽이 낮아 batch size가 차지 않아도 최대 flushIntervalMillis 안에 Redis로 밀어낸다.
        executor.scheduleWithFixedDelay(
                this::flushSafely,
                flushIntervalMillis,
                flushIntervalMillis,
                TimeUnit.MILLISECONDS
        );
        executor.scheduleWithFixedDelay(
                this::logQueueSize,
                QUEUE_SIZE_LOG_INTERVAL_MILLIS,
                QUEUE_SIZE_LOG_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public void stop() {
        flushSafely();
        executor.shutdown();
    }

    @Override
    public void save(ProductRankingScoreDelta delta) {
        queue.add(delta);

        if (queue.size() >= batchSize) {
            // 여러 요청이 동시에 예약해도 flushSafely의 guard가 실제 flush 중복 실행을 막는다.
            executor.execute(this::flushSafely);
        }
    }

    void flushSafely() {
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            flushAvailable();
        } catch (RuntimeException exception) {
            log.error("Failed to flush product ranking score batch. queuedDeltaCount={}", queue.size(), exception);
        } finally {
            flushing.set(false);
        }
    }

    int queuedDeltaCount() {
        return queue.size();
    }

    private void flushAvailable() {
        List<ProductRankingScoreDelta> batch = drainBatch();
        if (batch.isEmpty()) {
            return;
        }

        long flushStartedAt = System.nanoTime();

        // Redis bucket과 같은 기준으로 압축해 batch 안의 중복 ZINCRBY 명령 수를 줄인다.
        long compressStartedAt = System.nanoTime();
        List<ProductRankingScoreDelta> compressedBatch = productRankingScoreCompressor.compress(batch);
        long compressNanos = System.nanoTime() - compressStartedAt;

        // 실시간 랭킹 저장소 반영을 먼저 처리한다.
        long rankingStoreStartedAt = System.nanoTime();
        productRankingStore.increaseScores(compressedBatch);
        long rankingStoreNanos = System.nanoTime() - rankingStoreStartedAt;

        // ClickHouse는 병행 적재 대상이다. 실패해도 Redis 반영 성공을 되돌리지 않는다.
        long clickHouseNanos = flushClickHouse(compressedBatch);
        long totalNanos = System.nanoTime() - flushStartedAt;

        log.info(
                "Flushed product ranking score batch. batchSize={}, compressedBatchSize={}, queuedDeltaCount={}, "
                        + "compressMillis={}, rankingStoreMillis={}, clickHouseMillis={}, totalMillis={}, "
                        + "rankingStoreType={}, clickHouseEnabled={}",
                batch.size(),
                compressedBatch.size(),
                queue.size(),
                toMillis(compressNanos),
                toMillis(rankingStoreNanos),
                toMillis(clickHouseNanos),
                toMillis(totalNanos),
                productRankingStore.getClass().getSimpleName(),
                clickHouseProductRankingStore != null
        );
    }

    /**
     * clickHouse에 batch 단위로 점수를 반영한다.
     */
    private long flushClickHouse(List<ProductRankingScoreDelta> compressedBatch) {
        // ClickHouse 기능이 꺼져 있으면 추가 I/O 없이 바로 끝난다.
        if (clickHouseProductRankingStore == null) {
            return 0L;
        }

        long startedAt = System.nanoTime();
        try {
            clickHouseProductRankingStore.increaseScores(compressedBatch);
        } catch (RuntimeException exception) {
            // ClickHouse 장애가 요청 수신/Redis 실시간 랭킹 경로를 막지 않도록 여기서 격리한다.
            log.error("Failed to flush product ranking score batch to ClickHouse. batchSize={}",
                    compressedBatch.size(), exception);
        }
        return System.nanoTime() - startedAt;
    }

    /** 락 없이 한 번에 데이터를 꺼낸다 */
    private List<ProductRankingScoreDelta> drainBatch() {
        List<ProductRankingScoreDelta> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }

    private void logQueueSize() {
        log.info("Product ranking write buffer queue size. queuedDeltaCount={}", queue.size());
    }

    private long toMillis(long nanos) {
        return nanos / NANOS_PER_MILLI;
    }
}
