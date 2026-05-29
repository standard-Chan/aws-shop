package jeong.awsshop.analytics.application;

import java.time.Clock;
import java.time.Instant;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
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
        AnalyticsEventMessage event = newEvent(
                AnalyticsEventType.SEARCH,
                request.userId(),
                request.keyword(),
                null,
                null
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordProductView(ProductViewEventRequest request) {
        AnalyticsEventMessage event = newEvent(
                AnalyticsEventType.PRODUCT_VIEW,
                request.userId(),
                null,
                request.productId(),
                null
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordAddToCart(AddToCartEventRequest request) {
        AnalyticsEventMessage event = newEvent(
                AnalyticsEventType.ADD_TO_CART,
                request.userId(),
                null,
                request.productId(),
                null
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordPurchase(PurchaseEventRequest request) {
        AnalyticsEventMessage event = newEvent(
                AnalyticsEventType.PURCHASE,
                request.userId(),
                null,
                null,
                request.orderId()
        );
        analyticsEventPublisher.publish(event);
        return AnalyticsEventResponse.from(event);
    }

    private AnalyticsEventMessage newEvent(
            AnalyticsEventType eventType,
            Long userId,
            String keyword,
            Long productId,
            Long orderId
    ) {
        return new AnalyticsEventMessage(
                snowflakeIdGenerator.nextId(),
                eventType,
                userId,
                Instant.now(clock),
                keyword,
                productId,
                orderId
        );
    }
}
