package jeong.awsshop.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ReviewDto(
        Float rating,
        String title,
        String text,
        List<ReviewImageDto> images,
        String asin,
        @JsonProperty("parent_asin")
        String parentAsin,
        @JsonProperty("user_id")
        String userId,
        Long timestamp,
        @JsonProperty("helpful_vote")
        Integer helpfulVote,
        @JsonProperty("verified_purchase")
        Boolean verifiedPurchase
) {

    public List<ReviewImageDto> imagesOrEmpty() {
        if (images == null) {
            return List.of();
        }
        return images;
    }
}
