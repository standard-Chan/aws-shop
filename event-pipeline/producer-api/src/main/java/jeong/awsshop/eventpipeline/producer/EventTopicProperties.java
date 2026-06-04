package jeong.awsshop.eventpipeline.producer;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "event-pipeline.topics")
public record EventTopicProperties(
        String search,
        String productView,
        String cart,
        String purchase
) {

    public String topicOf(UserBehaviorEventType eventType) {
        return switch (eventType) {
            case SEARCH -> search;
            case PRODUCT_VIEW -> productView;
            case ADD_TO_CART -> cart;
            case PURCHASE -> purchase;
        };
    }
}
