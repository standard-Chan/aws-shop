package jeong.awsshop.eventpipeline.producer.controller;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.producer.EventIdGenerator;
import jeong.awsshop.eventpipeline.producer.EventTopicProperties;
import jeong.awsshop.eventpipeline.producer.controller.dto.AddToCartEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.EventAcceptedResponse;
import jeong.awsshop.eventpipeline.producer.controller.dto.ProductViewEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.PurchaseEventRequest;
import jeong.awsshop.eventpipeline.producer.controller.dto.SearchEventRequest;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-pipeline/events")
public class UserBehaviorEventController {

    private final EventIdGenerator eventIdGenerator;
    private final KafkaTemplate<String, UserBehaviorEventMessage> kafkaTemplate;
    private final EventTopicProperties topicProperties;
    private final Clock clock;

    public UserBehaviorEventController(
            EventIdGenerator eventIdGenerator,
            KafkaTemplate<String, UserBehaviorEventMessage> kafkaTemplate,
            EventTopicProperties topicProperties
    ) {
        this.eventIdGenerator = eventIdGenerator;
        this.kafkaTemplate = kafkaTemplate;
        this.topicProperties = topicProperties;
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
        publish(message);
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
        publish(message);
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
        publish(message);
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
        publish(message);
        return EventAcceptedResponse.from(message);
    }

    private void publish(UserBehaviorEventMessage message) {
        // 동일한 user에 대해서는 순서를 보장할 것이므로, key로 userId를 사용
        kafkaTemplate.send(
            topicProperties.topicOf(message.eventType()),
            String.valueOf(message.userId()),
            message
        );
    }
}
