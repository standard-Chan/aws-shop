package jeong.awsshop.analytics.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jeong.awsshop.analytics.domain.AnalyticsEventType;

public record AnalyticsBatchEventRequestItem(
        @NotNull AnalyticsEventType eventType,
        @NotNull
        @Positive Long userId,
        String keyword,
        @Positive Long productId,
        @Positive Long orderId,
        @Positive Long searchEventId
) {

    @AssertTrue
    public boolean isSearchEventValid() {
        if (eventType != AnalyticsEventType.SEARCH) {
            return true;
        }
        return keyword != null && !keyword.isBlank();
    }

    @AssertTrue
    public boolean isProductViewEventValid() {
        if (eventType != AnalyticsEventType.PRODUCT_VIEW) {
            return true;
        }
        return productId != null && (keyword == null || !keyword.isBlank());
    }

    @AssertTrue
    public boolean isAddToCartEventValid() {
        if (eventType != AnalyticsEventType.ADD_TO_CART) {
            return true;
        }
        return productId != null;
    }

    @AssertTrue
    public boolean isPurchaseEventValid() {
        if (eventType != AnalyticsEventType.PURCHASE) {
            return true;
        }
        return orderId != null;
    }
}
