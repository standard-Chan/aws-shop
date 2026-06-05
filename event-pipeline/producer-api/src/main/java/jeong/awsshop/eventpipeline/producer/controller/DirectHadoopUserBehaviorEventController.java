package jeong.awsshop.eventpipeline.producer.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.producer.EventIdGenerator;
import jeong.awsshop.eventpipeline.producer.controller.dto.AddToCartEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.EventAcceptedResponse;
import jeong.awsshop.eventpipeline.producer.controller.dto.ProductViewEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.PurchaseEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.SearchEventRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-pipeline/direct-hadoop/events")
public class DirectHadoopUserBehaviorEventController {

    private final EventIdGenerator eventIdGenerator;
    private final DirectHadoopUserBehaviorEventFileSink eventFileSink;
    private final Clock clock;

    public DirectHadoopUserBehaviorEventController(
            EventIdGenerator eventIdGenerator,
            DirectHadoopUserBehaviorEventFileSink eventFileSink
    ) {
        this.eventIdGenerator = eventIdGenerator;
        this.eventFileSink = eventFileSink;
        this.clock = Clock.systemUTC();
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
        save(message);
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
        save(message);
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
        save(message);
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
        save(message);
        return EventAcceptedResponse.from(message);
    }

    private void save(UserBehaviorEventMessage message) {
        eventFileSink.append(message);
    }
}

@Component
class DirectHadoopUserBehaviorEventFileSink {

    private final ObjectMapper objectMapper;
    private final Path outputPath;
    private final ReentrantLock lock = new ReentrantLock();

    DirectHadoopUserBehaviorEventFileSink(
            @Value("${event-pipeline.direct-hadoop.output-path:/tmp/aws-shop-event-pipeline/direct-user-behavior-events.jsonl}")
            Path outputPath,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.outputPath = outputPath;
    }

    void append(UserBehaviorEventMessage message) {
        lock.lock();
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    outputPath,
                    toJsonLine(message),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("[DirectHadoopProducer] event Append 에 실패하였습니다. path=" + outputPath, exception);
        } finally {
            lock.unlock();
        }
    }

    private String toJsonLine(UserBehaviorEventMessage message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message) + System.lineSeparator();
    }
}
