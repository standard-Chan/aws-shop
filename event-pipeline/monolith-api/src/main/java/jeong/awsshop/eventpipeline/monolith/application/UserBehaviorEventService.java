package jeong.awsshop.eventpipeline.monolith.application;

import java.time.Clock;
import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventSink;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.EventAcceptedResponse;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.SearchEventRequest;
import org.springframework.stereotype.Service;

@Service
public class UserBehaviorEventService {

    private final EventIdGenerator eventIdGenerator;
    private final UserBehaviorEventSink eventSink;
    private final Clock clock;

    public UserBehaviorEventService(EventIdGenerator eventIdGenerator, UserBehaviorEventSink eventSink, Clock clock) {
        this.eventIdGenerator = eventIdGenerator;
        this.eventSink = eventSink;
        this.clock = clock;
    }

    public EventAcceptedResponse recordSearch(SearchEventRequest request) {
        UserBehaviorEventMessage event = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.SEARCH,
                request.userId(),
                now(),
                request.keyword(),
                null,
                null,
                null
        );
        save(event);
        return EventAcceptedResponse.from(event);
    }

    public EventAcceptedResponse recordProductView(ProductViewEventRequest request) {
        UserBehaviorEventMessage event = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.PRODUCT_VIEW,
                request.userId(),
                now(),
                request.searchKeyword(),
                request.productId(),
                null,
                request.searchEventId()
        );
        save(event);
        return EventAcceptedResponse.from(event);
    }

    public EventAcceptedResponse recordAddToCart(AddToCartEventRequest request) {
        UserBehaviorEventMessage event = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.ADD_TO_CART,
                request.userId(),
                now(),
                null,
                request.productId(),
                null,
                null
        );
        save(event);
        return EventAcceptedResponse.from(event);
    }

    public EventAcceptedResponse recordPurchase(PurchaseEventRequest request) {
        UserBehaviorEventMessage event = UserBehaviorEventMessage.newMessage(
                eventIdGenerator.nextId(),
                UserBehaviorEventType.PURCHASE,
                request.userId(),
                now(),
                null,
                null,
                request.orderId(),
                null
        );
        save(event);
        return EventAcceptedResponse.from(event);
    }

    /**
     * UserBehaviorEventMessage를 UserBehaviorEventSink에 저장한다.
     * @param event
     */
    private void save(UserBehaviorEventMessage event) {
        eventSink.save(event);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
