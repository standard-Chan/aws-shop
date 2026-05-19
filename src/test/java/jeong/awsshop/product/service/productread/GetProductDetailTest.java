package jeong.awsshop.product.service.productread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jeong.awsshop.product.exception.productread.ProductNotFoundException;
import jeong.awsshop.product.repository.ProductBoughtTogetherRepository;
import jeong.awsshop.product.repository.ProductCategoryRepository;
import jeong.awsshop.product.repository.ProductDescriptionRepository;
import jeong.awsshop.product.repository.ProductFeatureRepository;
import jeong.awsshop.product.repository.ProductImageRepository;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.ProductVideoRepository;
import jeong.awsshop.product.repository.projection.ProductBoughtTogetherDetailProjection;
import jeong.awsshop.product.repository.projection.ProductCategoryDetailProjection;
import jeong.awsshop.product.repository.projection.ProductDescriptionDetailProjection;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductFeatureDetailProjection;
import jeong.awsshop.product.repository.projection.ProductImageDetailProjection;
import jeong.awsshop.product.repository.projection.ProductVideoDetailProjection;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProductDetailTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductFeatureRepository productFeatureRepository;

    @Mock
    private ProductDescriptionRepository productDescriptionRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ProductBoughtTogetherRepository productBoughtTogetherRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductVideoRepository productVideoRepository;

    @InjectMocks
    private ProductReadService productReadService;

    @Test
    @DisplayName("상품 상세 조회 시 Product 본문과 모든 child collection을 조립해야 한다")
    void should_return_all_product_detail_fields_when_product_exists() {
        // Given: Product 본문과 모든 child projection fixture를 준비한다
        Long productId = 9_000_000_000_000L;
        stubProductDetail(productId, detailsJson());
        stubAllChildren(productId);

        // When: Product 상세 정보를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: Product 본문 필드는 모두 응답에 포함되어야 한다
        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.parentAsin()).isEqualTo("B07NK78DVV");
        assertThat(response.title()).isEqualTo("Psychedelic Swirls Key Fob");
        assertThat(response.averageRating()).isEqualByComparingTo("4.9");
        assertThat(response.ratingNumber()).isEqualTo(14);
        assertThat(response.price()).isEqualByComparingTo("17.99");
        assertThat(response.store()).isEqualTo("Green Acorn Kitchen");

        // Then: child collection도 상세 응답에 모두 포함되어야 한다
        assertThat(response.features()).hasSize(1);
        assertThat(response.descriptions()).hasSize(1);
        assertThat(response.categories()).hasSize(1);
        assertThat(response.boughtTogether()).hasSize(1);
        assertThat(response.images()).hasSize(1);
        assertThat(response.videos()).hasSize(1);
    }

    @Test
    @DisplayName("상품 상세 조회 시 Product가 없으면 ProductNotFoundException을 던져야 한다")
    void should_throw_product_not_found_exception_when_product_does_not_exist() {
        // Given: Product 본문 projection이 존재하지 않는다
        Long productId = 9_999_999_999_999L;
        when(productRepository.findDetailById(productId)).thenReturn(Optional.empty());

        // When & Then: 없는 Product는 상세 조회 실패 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductDetail(productId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("child collection이 없으면 null이 아니라 빈 list를 반환해야 한다")
    void should_return_empty_collections_when_children_do_not_exist() {
        // Given: Product 본문은 존재하지만 child projection은 모두 비어 있다
        Long productId = 9_000_000_000_000L;
        stubProductDetail(productId, detailsJson());
        stubEmptyChildren(productId);

        // When: Product 상세 정보를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: 모든 child collection은 빈 list로 정규화되어야 한다
        assertThat(response.features()).isEmpty();
        assertThat(response.descriptions()).isEmpty();
        assertThat(response.categories()).isEmpty();
        assertThat(response.boughtTogether()).isEmpty();
        assertThat(response.images()).isEmpty();
        assertThat(response.videos()).isEmpty();
    }

    @Test
    @DisplayName("details가 null이면 빈 map을 반환해야 한다")
    void should_return_empty_details_when_details_is_null() {
        // Given: details가 null인 Product 본문 projection
        Long productId = 9_000_000_000_000L;
        stubProductDetail(productId, null);
        stubEmptyChildren(productId);

        // When: Product 상세 정보를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: details는 null이 아니라 빈 map이어야 한다
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("details가 blank이면 빈 map을 반환해야 한다")
    void should_return_empty_details_when_details_is_blank() {
        // Given: details가 공백 문자열인 Product 본문 projection
        Long productId = 9_000_000_000_000L;
        stubProductDetail(productId, "   ");
        stubEmptyChildren(productId);

        // When: Product 상세 정보를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: blank details는 빈 map으로 정규화되어야 한다
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("details JSON object는 Map으로 파싱해서 반환해야 한다")
    void should_parse_details_json_to_map_when_details_json_exists() {
        // Given: nested value를 포함한 details JSON
        Long productId = 9_000_000_000_000L;
        stubProductDetail(productId, detailsJson());
        stubEmptyChildren(productId);

        // When: Product 상세 정보를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: details는 문자열이 아니라 JSON object 구조여야 한다
        assertThat(response.details())
                .containsEntry("Department", "unisex-adult")
                .containsEntry("Number of Items", 1);
        assertThat(response.details().get("Package")).isInstanceOf(Map.class);
    }

    private void stubProductDetail(Long productId, String details) {
        when(productRepository.findDetailById(productId))
                .thenReturn(Optional.of(productDetail(productId, details)));
    }

    private void stubAllChildren(Long productId) {
        when(productFeatureRepository.findFeatureDetailsByProductId(productId))
                .thenReturn(List.of(feature(0, "6 x 1 loop with swivel clip")));
        when(productDescriptionRepository.findDescriptionDetailsByProductId(productId))
                .thenReturn(List.of(description(0, "A colorful way to carry your keys")));
        when(productCategoryRepository.findCategoryDetailsByProductId(productId))
                .thenReturn(List.of(category("Handmade Products")));
        when(productBoughtTogetherRepository.findBoughtTogetherDetailsByProductId(productId))
                .thenReturn(List.of(boughtTogether(9_000_000_000_001L)));
        when(productImageRepository.findImageDetailsByProductId(productId))
                .thenReturn(List.of(image("MAIN")));
        when(productVideoRepository.findVideoDetailsByProductId(productId))
                .thenReturn(List.of(video("Product video")));
    }

    private void stubEmptyChildren(Long productId) {
        when(productFeatureRepository.findFeatureDetailsByProductId(productId)).thenReturn(List.of());
        when(productDescriptionRepository.findDescriptionDetailsByProductId(productId)).thenReturn(List.of());
        when(productCategoryRepository.findCategoryDetailsByProductId(productId)).thenReturn(List.of());
        when(productBoughtTogetherRepository.findBoughtTogetherDetailsByProductId(productId)).thenReturn(List.of());
        when(productImageRepository.findImageDetailsByProductId(productId)).thenReturn(List.of());
        when(productVideoRepository.findVideoDetailsByProductId(productId)).thenReturn(List.of());
    }

    private String detailsJson() {
        return """
                {
                  "Department": "unisex-adult",
                  "Number of Items": 1,
                  "Package": {
                    "Weight": "0.78 Ounces"
                  }
                }
                """;
    }

    private ProductDetailProjection productDetail(Long id, String details) {
        return new ProductDetailProjection() {
            public Long getId() { return id; }
            public String getParentAsin() { return "B07NK78DVV"; }
            public String getTitle() { return "Psychedelic Swirls Key Fob"; }
            public String getMainCategory() { return "HANDMADE"; }
            public BigDecimal getAverageRating() { return new BigDecimal("4.9"); }
            public Integer getRatingNumber() { return 14; }
            public BigDecimal getPrice() { return new BigDecimal("17.99"); }
            public String getStore() { return "Green Acorn Kitchen"; }
            public String getDetails() { return details; }
        };
    }

    private ProductFeatureDetailProjection feature(Integer featureIndex, String feature) {
        return new ProductFeatureDetailProjection() {
            public Integer getFeatureIndex() { return featureIndex; }
            public String getFeature() { return feature; }
        };
    }

    private ProductDescriptionDetailProjection description(Integer descriptionIndex, String description) {
        return new ProductDescriptionDetailProjection() {
            public Integer getDescriptionIndex() { return descriptionIndex; }
            public String getDescription() { return description; }
        };
    }

    private ProductCategoryDetailProjection category(String category) {
        return new ProductCategoryDetailProjection() {
            public String getCategory() { return category; }
        };
    }

    private ProductBoughtTogetherDetailProjection boughtTogether(Long relatedProductId) {
        return new ProductBoughtTogetherDetailProjection() {
            public Long getRelatedProductId() { return relatedProductId; }
            public String getRelatedProductTitle() { return "Related product"; }
            public String getRelatedProductImageUrl() { return "related-image"; }
        };
    }

    private ProductImageDetailProjection image(String variant) {
        return new ProductImageDetailProjection() {
            public String getVariant() { return variant; }
            public String getThumb() { return "main-thumb"; }
            public String getLarge() { return "main-large"; }
            public String getHiRes() { return "main-hires"; }
        };
    }

    private ProductVideoDetailProjection video(String title) {
        return new ProductVideoDetailProjection() {
            public String getTitle() { return title; }
            public String getUrl() { return "https://example.com/video"; }
            public String getUserId() { return "user-1"; }
        };
    }
}
