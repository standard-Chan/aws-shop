package jeong.awsshop.analytics.application;

import java.time.Clock;
import java.time.Instant;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsEventResponse;
import jeong.awsshop.analytics.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.analytics.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.analytics.presentation.dto.SearchEventRequest;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsEventService {

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final Clock clock;

    public AnalyticsEventResponse recordSearch(SearchEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.search(
                nextEventId(),
                request.userId(),
                now(),
                request.keyword()
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordProductView(ProductViewEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.productView(
                nextEventId(),
                request.userId(),
                now(),
                request.productId(),
                request.searchEventId(),
                request.searchKeyword()
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordAddToCart(AddToCartEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.addToCart(
                nextEventId(),
                request.userId(),
                now(),
                request.productId()
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordPurchase(PurchaseEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.purchase(
                nextEventId(),
                request.userId(),
                now(),
                request.orderId()
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    private Long nextEventId() {
        return snowflakeIdGenerator.nextId();
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
