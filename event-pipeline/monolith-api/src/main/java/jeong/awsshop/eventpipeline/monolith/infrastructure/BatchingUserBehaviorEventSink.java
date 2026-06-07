package jeong.awsshop.eventpipeline.monolith.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.monolith.domain.SerializedUserBehaviorEvent;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventSink;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BatchingUserBehaviorEventSink implements UserBehaviorEventSink {

    private static final Logger log = LoggerFactory.getLogger(BatchingUserBehaviorEventSink.class);

    /*
     * HTTP 요청 thread와 저장 worker thread 사이의 경계다.
     *
     * 의도:
     * - 최대한 많은 요청을 처리하기 위해 batch 처리로 효율을 높였다.
     * - 요청 thread는 외부 I/O(HDFS/ES 응답)를 기다리지 않고 queue에 넣은 뒤 바로 202를 반환한다.
     * - 실제 저장 및 요청 전송은 executor의 단일 worker가 batch 단위로 처리한다.
     */
    private final BlockingQueue<UserBehaviorEventMessage> queue = new LinkedBlockingQueue<>(100000);

    /*
     * batch flush가 동시에 두 번 실행되는 것을 막는 guard다.
     *
     * flush는 두 경로에서 시작될 수 있다.
     * - batchSize 이상 쌓였을 때 save 메서드가 executor에 flush 작업을 예약
     * - 일정 tick마다 scheduleWithFixedDelay가 flush 실행
     *
     * 두 경로가 겹쳐도 같은 queue를 동시에 drain하지 않게 하기 위해 AtomicBoolean으로
     * "현재 flush 중인지"를 compare-and-set으로 확인한다.
     */
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final int batchSize;
    private final long flushIntervalMillis;

    /*
     * JSON 변환은 여기에서 한 번만 수행한다.
     */
    private final ObjectMapper objectMapper;

    /*
     * 실제 저장 대상들이다.
     * 현재 : Hadoop staging 파일 sink와 Elasticsearch bulk sink가 들어온다.
     * Spring @Order 값에 따라 파일 저장(10), ES 저장(20) 순서로 호출된다.
     */
    private final List<UserBehaviorEventStorage> storages;

    // 저장 I/O worker
    private final ScheduledExecutorService executor;

    /*
     * Spring이 사용하는 운영 생성자다.
     * 아래 package-private 생성자는 테스트에서 executor를 직접 주입하기 위한 용도이므로,
     * 생성자가 2개인 상황에서 Spring이 이 생성자를 선택하도록 @Autowired를 명시한다.
     */
    @Autowired
    public BatchingUserBehaviorEventSink(
            @Value("${event-pipeline.monolith.batch.size:30000}") int batchSize,
            @Value("${event-pipeline.monolith.batch.flush-interval-millis:5000}") long flushIntervalMillis,
            ObjectMapper objectMapper,
            List<UserBehaviorEventStorage> storages
    ) {
        this(
                batchSize,
                flushIntervalMillis,
                objectMapper,
                storages,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "user-behavior-event-batch-writer");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    // 테스트 전용 생성자
    BatchingUserBehaviorEventSink(
            int batchSize,
            long flushIntervalMillis,
            ObjectMapper objectMapper,
            List<UserBehaviorEventStorage> storages,
            ScheduledExecutorService executor
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (flushIntervalMillis <= 0) {
            throw new IllegalArgumentException("flushIntervalMillis must be positive");
        }
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.objectMapper = objectMapper;
        this.storages = List.copyOf(storages);
        this.executor = executor;
    }

    @PostConstruct
    public void start() {
        // 일정 시간마다 저장소로 밀어낸다.
        executor.scheduleWithFixedDelay(
                this::flushSafely,
                flushIntervalMillis,
                flushIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public void stop() {
        /*
         * 애플리케이션 종료 직전에 남은 이벤트를 최대한 저장한다.
         * shutdownNow가 아니라 shutdown을 쓰는 이유는 이미 예약된 저장 작업이 자연스럽게 끝나도록 하기 위해서다.
         */
        flushSafely();
        executor.shutdown();
    }

    // 외부 IO, HTTP 호출은 이 요청 thread에서 하지 않는다. 즉시 성공을 반환한다.
    @Override
    public void save(UserBehaviorEventMessage event) {
        queue.add(event);

        if (queue.size() >= batchSize) {
            // batch 크기가 찼다면 flush 작업을 예약한다.
            // flushSafely 내부 guard가 있으므로 여러 요청이 동시에 이 분기를 타도 실제 flush는 하나씩만 실행된다.
            executor.execute(this::flushSafely);
        }
    }

    void flushSafely() {
        // 락 (이미 처리중이면 pass 한다)
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            flushAvailable();
        } catch (RuntimeException exception) {
            log.error("Failed to flush user behavior event batch. queuedEventCount={}", queue.size(), exception);
        } finally {
            flushing.set(false);
        }
    }

    int queuedEventCount() {
        return queue.size();
    }

    private void flushAvailable() {
        // 현재 queue에 쌓인 이벤트를 batchSize 단위로 비운다.
        while (!queue.isEmpty()) {
            List<UserBehaviorEventMessage> batch = drainBatch();
            if (batch.isEmpty()) {
                return;
            }
            List<SerializedUserBehaviorEvent> serializedEvents = serialize(batch);
            for (UserBehaviorEventStorage storage : storages) {
                // 각 저장소에 batch 단위로 저장/전달한다.
                storage.saveAll(serializedEvents);
            }
        }
    }

    /**
     * batch 단위로 queue에서 이벤트를 꺼낸다
     */
    private List<UserBehaviorEventMessage> drainBatch() {
        List<UserBehaviorEventMessage> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }

    /**
     * 외부 저장소에 전달할 때, 사용하는 공통 데이터 포맷의 batch로 변환한다.
     */
    private List<SerializedUserBehaviorEvent> serialize(List<UserBehaviorEventMessage> batch) {
        return batch.stream()
                .map(this::serialize)
                .toList();
    }

    /**
     * 외부 저장소에 전달할 때, 사용하는 공통 데이터 포맷으로 변환한다.
     */
    private SerializedUserBehaviorEvent serialize(UserBehaviorEventMessage event) {
        try {
            return new SerializedUserBehaviorEvent(event.eventId(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize user behavior event. eventId=" + event.eventId(), exception);
        }
    }
}
