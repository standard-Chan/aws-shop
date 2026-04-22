package jeong.awsshop.product.controller.getproductdetail;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jeong.awsshop.product.controller.ProductController;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.exception.productread.ProductNotFoundException;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductBoughtTogetherResponse;
import jeong.awsshop.product.service.productread.dto.ProductCategoryResponse;
import jeong.awsshop.product.service.productread.dto.ProductDescriptionResponse;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import jeong.awsshop.product.service.productread.dto.ProductFeatureResponse;
import jeong.awsshop.product.service.productread.dto.ProductImageResponse;
import jeong.awsshop.product.service.productread.dto.ProductVideoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerGetProductDetailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductReadService productReadService;

    @Test
    @DisplayName("상품 상세 조회 요청이면 id를 service에 전달해야 한다")
    void should_call_service_with_id_when_product_detail_is_requested() throws Exception {
        // Given: 상세 조회 service가 완성된 응답을 반환하도록 준비한다
        Long productId = 9_000_000_000_000L;
        when(productReadService.getProductDetail(productId))
                .thenReturn(detailResponse(productId));

        // When: Product id로 상세 조회 API를 호출한다
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk());

        // Then: controller는 path variable id를 service에 그대로 전달해야 한다
        verify(productReadService).getProductDetail(productId);
    }

    @Test
    @DisplayName("상품 상세 조회가 성공하면 상세 응답을 JSON으로 반환해야 한다")
    void should_return_product_detail_json_when_product_exists() throws Exception {
        // Given: 상세 페이지에 필요한 모든 정보를 가진 응답을 준비한다
        Long productId = 9_000_000_000_000L;
        when(productReadService.getProductDetail(productId))
                .thenReturn(detailResponse(productId));

        // When & Then: controller는 service 응답을 HTTP JSON으로 직렬화해야 한다
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.parentAsin").value("B07NK78DVV"))
                .andExpect(jsonPath("$.title").value("Psychedelic Swirls Key Fob"))
                .andExpect(jsonPath("$.details.Department").value("unisex-adult"))
                .andExpect(jsonPath("$.features[0].feature").value("6 x 1 loop with swivel clip"))
                .andExpect(jsonPath("$.images[0].variant").value("MAIN"));
    }

    @Test
    @DisplayName("상품 상세 조회에서 id가 양수가 아니면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_id_is_zero() throws Exception {
        // Given: 유효하지 않은 id 0

        // When & Then: 양수가 아닌 id는 요청 단계에서 거절되어야 한다
        mockMvc.perform(get("/api/products/{id}", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 상세 조회에서 id가 숫자가 아니면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_id_is_not_number() throws Exception {
        // Given: Long으로 변환할 수 없는 path variable

        // When & Then: type mismatch는 400 응답이어야 한다
        mockMvc.perform(get("/api/products/{id}", "not-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 상세 조회에서 Product가 없으면 404 Not Found를 반환해야 한다")
    void should_return_not_found_when_product_does_not_exist() throws Exception {
        // Given: service가 존재하지 않는 Product 예외를 던진다
        Long productId = 9_999_999_999_999L;
        when(productReadService.getProductDetail(productId))
                .thenThrow(new ProductNotFoundException());

        // When & Then: 없는 Product는 404로 응답해야 한다
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());
    }

    private ProductDetailResponse detailResponse(Long productId) {
        // 상세 응답 fixture: Product 본문과 모든 child collection을 포함한다
        return new ProductDetailResponse(
                productId,
                "B07NK78DVV",
                "Psychedelic Swirls Key Fob",
                MainCategory.HANDMADE,
                new BigDecimal("4.9"),
                14,
                new BigDecimal("17.99"),
                "Green Acorn Kitchen",
                Map.of("Department", "unisex-adult"),
                List.of(new ProductFeatureResponse(0, "6 x 1 loop with swivel clip")),
                List.of(new ProductDescriptionResponse(0, "A colorful way to carry your keys")),
                List.of(new ProductCategoryResponse("Handmade Products")),
                List.of(new ProductBoughtTogetherResponse(
                        9_000_000_000_001L,
                        "Related product",
                        "related-image"
                )),
                List.of(new ProductImageResponse("MAIN", "main-thumb", "main-large", "main-hires")),
                List.of(new ProductVideoResponse("Product video", "https://example.com/video", "user-1"))
        );
    }
}
