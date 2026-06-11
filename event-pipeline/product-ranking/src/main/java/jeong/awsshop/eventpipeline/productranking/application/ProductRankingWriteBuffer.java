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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductRankingWriteBuffer implements ProductRankingScoreWriter {

    private static final Logger log = LoggerFactory.getLogger(ProductRankingWriteBuffer.class);

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
    private final ProductRankingScoreCompressor productRankingScoreCompressor = new ProductRankingScoreCompressor();
    private final ScheduledExecutorService executor;

    @Autowired
    public ProductRankingWriteBuffer(
            @Value("${event-pipeline.product-ranking.batch.size:5000}") int batchSize,
            @Value("${event-pipeline.product-ranking.batch.flush-interval-millis:500}") long flushIntervalMillis,
            @Value("${event-pipeline.product-ranking.batch.queue-capacity:100000}") int queueCapacity,
            ProductRankingStore productRankingStore
    ) {
        this(
                batchSize,
                flushIntervalMillis,
                queueCapacity,
                productRankingStore,
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

    /**
     * 무한 루프로 작성한 이유 : flush interval까지 대기하지 않고, 한번에 비우기 위함이다.
     * 만약 대기해야한다면, flush -> batch 처리, -> 1초 대기 -> flush -> batch 처리 -> 1초 대기 ... 이런식으로 진행되어,
     *  batch 처리 후 최대 flush interval 만큼 Redis 반영이 지연될 수 있다.
     */
    private void flushAvailable() {
        while (!queue.isEmpty()) {
            List<ProductRankingScoreDelta> batch = drainBatch();
            if (batch.isEmpty()) {
                return;
            }
            // Redis bucket과 같은 기준으로 압축해 batch 안의 중복 ZINCRBY 명령 수를 줄인다.
            productRankingStore.increaseScores(productRankingScoreCompressor.compress(batch));
        }
    }

    /** 락 없이 한 번에 데이터를 꺼낸다 */
    private List<ProductRankingScoreDelta> drainBatch() {
        List<ProductRankingScoreDelta> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }
}
