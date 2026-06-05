package jeong.awsshop.eventpipeline.producer.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.producer.EventIdGenerator;
import jeong.awsshop.eventpipeline.producer.controller.dto.AddToCartEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.EventAcceptedResponse;
import jeong.awsshop.eventpipeline.producer.controller.dto.ProductViewEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.PurchaseEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.SearchEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/event-pipeline/async-hadoop/events")
public class AsyncHadoopUserBehaviorEventController {

    private final EventIdGenerator eventIdGenerator;
    private final AsyncHadoopUserBehaviorEventFileSink eventFileSink;
    private final Clock clock;

    private final WebClient webClient;

    public AsyncHadoopUserBehaviorEventController(
        EventIdGenerator eventIdGenerator,
        AsyncHadoopUserBehaviorEventFileSink eventFileSink,
        WebClient webClient
    ) {
        this.eventIdGenerator = eventIdGenerator;
        this.eventFileSink = eventFileSink;
        this.webClient = webClient;
        this.clock = Clock.systemUTC();
    }

    private void sendDummyRequests(UserBehaviorEventMessage message) {

        // POST #1
        webClient.post()
            .uri("http://dummy-api-1.local/events")
            .bodyValue(message)
            .retrieve()
            .toBodilessEntity()
            .subscribe();

        // POST #2
        webClient.post()
            .uri("http://dummy-api-2.local/events")
            .bodyValue(message)
            .retrieve()
            .toBodilessEntity()
            .subscribe();
    }

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse search(@Valid @RequestBody SearchEventRequest request) {
        UserBehaviorEventMessage message = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.SEARCH,
                request.userId(),
                Instant.now(clock),
                request.keyword(),
                null,
                null,
                null
        );
        saveAsync(message);
        sendDummyRequests(message);
        return EventAcceptedResponse.from(message);
    }

    @PostMapping("/product-view")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse productView(@Valid @RequestBody ProductViewEventRequest request) {
        UserBehaviorEventMessage message = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.PRODUCT_VIEW,
                request.userId(),
                Instant.now(clock),
                request.searchKeyword(),
                request.productId(),
                null,
                request.searchEventId()
        );
        saveAsync(message);
        sendDummyRequests(message);
        return EventAcceptedResponse.from(message);
    }

    @PostMapping("/add-to-cart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse addToCart(@Valid @RequestBody AddToCartEventRequest request) {
        UserBehaviorEventMessage message = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.ADD_TO_CART,
                request.userId(),
                Instant.now(clock),
                null,
                request.productId(),
                null,
                null
        );
        saveAsync(message);
        sendDummyRequests(message);
        return EventAcceptedResponse.from(message);
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse purchase(@Valid @RequestBody PurchaseEventRequest request) {
        UserBehaviorEventMessage message = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.PURCHASE,
                request.userId(),
                Instant.now(clock),
                null,
                null,
                request.orderId(),
                null
        );
        saveAsync(message);
        sendDummyRequests(message);
        return EventAcceptedResponse.from(message);
    }

    private void saveAsync(UserBehaviorEventMessage message) {
        eventFileSink.appendAsync(message);
    }
}
@Component
class AsyncHadoopUserBehaviorEventFileSink {

    private static final Logger log = LoggerFactory.getLogger(AsyncHadoopUserBehaviorEventFileSink.class);

    private final ObjectMapper objectMapper;
    private final Path outputPath;

    // 이벤트를 빠르게 적재할 메모리 버퍼 큐
    private final BlockingQueue<UserBehaviorEventMessage> queue;

    // 백그라운드에서 배치를 처리할 단일 스레드
    private Thread workerThread;
    private volatile boolean running = true;

    // 배치 설정 값 (기본값: 1000개 모이거나, 500ms 지나면 플러시)
    private final int batchSize;
    private final long flushIntervalMs;

    AsyncHadoopUserBehaviorEventFileSink(
        @Value("${event-pipeline.async-hadoop.output-path:/tmp/aws-shop-event-pipeline/async-user-behavior-events.jsonl}") Path outputPath,
        @Value("${event-pipeline.async-hadoop.queue-capacity:100000}") int queueCapacity,
        @Value("${event-pipeline.async-hadoop.batch-size:1000}") int batchSize,
        @Value("${event-pipeline.async-hadoop.flush-interval-ms:500}") long flushIntervalMs,
        ObjectMapper objectMapper
    ) {
        this.outputPath = outputPath;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        // 부모 디렉토리가 없으면 미리 생성
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            log.error("[AsyncHadoopProducer] 디렉토리 생성 실패", e);
        }

        // 백그라운드 저장 워커 시작
        this.workerThread = new Thread(this::processBatchLoop, "async-hadoop-file-writer");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * 컨트롤러 진입 스레드가 호출하는 메서드.
     * 메모리 큐에 넣고 즉시 리턴하므로 응답 속도가 극대화됩니다.
     */
    void appendAsync(UserBehaviorEventMessage message) {
        // queue.offer()는 큐가 가득 차면 즉시 false를 반환합니다.
        // 큐가 가득 찼을 때 대기하고 싶다면 queue.put(message)을 사용하세요.
        boolean success = queue.offer(message);
        if (!success) {
            throw new IllegalStateException(
                "[AsyncHadoopProducer] 큐가 가득 차서 이벤트를 저장할 수 없습니다. eventId=" + message.eventId()
            );
        }
    }

    private void processBatchLoop() {
        List<UserBehaviorEventMessage> batch = new ArrayList<>(batchSize);

        while (running || !queue.isEmpty()) {
            try {
                // 1. 첫 번째 아이템은 지정된 시간만큼 대기하며 가져옴 (시간 초과 이벤트 트리거용)
                UserBehaviorEventMessage firstMessage = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);

                if (firstMessage != null) {
                    batch.add(firstMessage);
                    // 2. 첫 아이템을 잡았다면, 지정된 배치 사이즈까지 남은 아이템들을 대기 없이 탈탈 털어옴(Drain)
                    queue.drainTo(batch, batchSize - 1);
                }

                // 3. 배치 크기가 찼거나, 시간이 만료되어 데이터가 모였다면 파일에 일괄 기록
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[AsyncHadoopProducer] 워커 스레드가 인터럽트 되었습니다. 잔여 데이터를 처리합니다.");
            } catch (Exception e) {
                log.error("[AsyncHadoopProducer] 배치 처리 중 예상치 못한 에러 발생", e);
            }
        }
    }

    private void flushBatch(List<UserBehaviorEventMessage> batch) {
        // BufferedWriter를 열어 한 번에 sequential 쓰기를 수행 (I/O 성능 극대화)
        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND)) {

            for (UserBehaviorEventMessage message : batch) {
                writer.write(objectMapper.writeValueAsString(message));
                writer.newLine();
            }
            writer.flush(); // 디스크 디바이스로 밀어넣기
        } catch (IOException e) {
            log.error("[AsyncHadoopProducer] 배치 파일 저장 실패. 손실된 이벤트 수: {}", batch.size(), e);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("[AsyncHadoopProducer] 종료 절차 시작. 큐에 남은 이벤트를 플러시합니다.");
        this.running = false;
        if (this.workerThread != null) {
            this.workerThread.interrupt();
            try {
                // 남은 데이터가 다 써질 때까지 최대 5초 대기
                this.workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("[AsyncHadoopProducer] 워커 스레드 종료 완료.");
    }
}

@Configuration
class AsyncHadoopUserBehaviorEventTaskExecutorConfig {

    @Bean(name = "asyncHadoopEventTaskExecutor")
    TaskExecutor asyncHadoopEventTaskExecutor(
            @Value("${event-pipeline.async-hadoop.core-pool-size:4}") int corePoolSize,
            @Value("${event-pipeline.async-hadoop.max-pool-size:16}") int maxPoolSize,
            @Value("${event-pipeline.async-hadoop.queue-capacity:100000}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("async-hadoop-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
