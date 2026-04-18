package jeong.awsshop.product.service.dataimport.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * JSONL 상품 원문 한 줄을 ObjectMapper로 바로 역직렬화하기 위한 DTO
 * 벌크 insert 단계에서 도메인 엔티티로 매핑하기 쉬운 구조를 유지
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        @JsonProperty("main_category") String mainCategory,
        @JsonProperty("title") String title,
        @JsonProperty("average_rating") BigDecimal averageRating,
        @JsonProperty("rating_number") Integer ratingNumber,
        @JsonProperty("features") List<String> features,
        @JsonProperty("description") List<String> descriptions,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("images") List<ProductImageDto> images,
        @JsonProperty("videos") List<ProductVideoDto> videos,
        @JsonProperty("store") String store,
        @JsonProperty("categories") List<String> categories,
        @JsonProperty("details") Map<String, String> details,
        @JsonProperty("parent_asin") String parentAsin,
        @JsonProperty("bought_together") BoughtTogetherDto boughtTogether
) {

    public List<String> featuresOrEmpty() {
        return features == null ? List.of() : features;
    }

    public List<String> descriptionsOrEmpty() {
        return descriptions == null ? List.of() : descriptions;
    }

    public List<ProductImageDto> imagesOrEmpty() {
        return images == null ? List.of() : images;
    }

    public List<ProductVideoDto> videosOrEmpty() {
        return videos == null ? List.of() : videos;
    }

    public List<String> categoriesOrEmpty() {
        return categories == null ? List.of() : categories;
    }

    public Map<String, String> detailsOrEmpty() {
        return details == null ? Map.of() : details;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductImageDto(
            @JsonProperty("thumb") String thumb,
            @JsonProperty("large") String large,
            @JsonProperty("variant") String variant,
            @JsonProperty("hi_res") String hiRes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductVideoDto(
            @JsonProperty("title") String title,
            @JsonProperty("url") String url,
            @JsonProperty("user_id") String userId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoughtTogetherDto(
            @JsonProperty("related_product_id")
            @JsonAlias("relatedProductId")
            Long relatedProductId,
            @JsonProperty("related_product_title")
            @JsonAlias("relatedProductTitle")
            String relatedProductTitle,
            @JsonProperty("related_product_image_url")
            @JsonAlias("relatedProductImageUrl")
            String relatedProductImageUrl
    ) {
    }
}
