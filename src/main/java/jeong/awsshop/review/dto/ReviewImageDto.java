package jeong.awsshop.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewImageDto(
        @JsonProperty("small_image_url")
        String smallImageUrl,
        @JsonProperty("medium_image_url")
        String mediumImageUrl,
        @JsonProperty("large_image_url")
        String largeImageUrl,
        @JsonProperty("attachment_type")
        String attachmentType
) {

    public boolean hasAnyStoredField() {
        return smallImageUrl != null
                || mediumImageUrl != null
                || largeImageUrl != null
                || attachmentType != null;
    }
}
