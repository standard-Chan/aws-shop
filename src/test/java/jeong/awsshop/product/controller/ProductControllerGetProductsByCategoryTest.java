package jeong.awsshop.product.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.CategoryCursor;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerGetProductsByCategoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductReadService productReadService;

    @Test
    @DisplayName("category 조회에서 size를 생략하면 기본 size 20으로 service를 호출해야 한다")
    void should_call_service_with_default_size_when_category_size_is_omitted() throws Exception {
        // Given: category 조회 service가 빈 응답을 반환하도록 준비한다
        when(productReadService.getProductsByCategory(
                "HANDMADE_PRODUCTS",
                20,
                null,
                null,
                null,
                true,
                false
        )).thenReturn(new ProductCategoryCursorResponse(List.of(), null, false));

        // When: size 없이 category 목록 API를 호출한다
        mockMvc.perform(get("/api/products/category")
                        .param("mainCategory", "HANDMADE_PRODUCTS")
                        .param("averageRating", "true"))
                .andExpect(status().isOk());

        // Then: controller는 기본 size 20과 query parameter를 service에 전달해야 한다
        verify(productReadService).getProductsByCategory(
                "HANDMADE_PRODUCTS",
                20,
                null,
                null,
                null,
                true,
                false
        );
    }

    @Test
    @DisplayName("category 조회 요청이면 category, cursor, 정렬 parameter를 service에 전달해야 한다")
    void should_call_service_with_category_cursor_and_sort_parameters_when_category_request_is_valid() throws Exception {
        // Given: averageRating cursor 요청과 service 응답을 준비한다
        Long cursorId = 9_000_000_000_000L;
        BigDecimal cursorAverageRating = new BigDecimal("4.5");
        ProductCategoryCursorResponse response = new ProductCategoryCursorResponse(
                List.of(),
                new CategoryCursor(cursorId, cursorAverageRating, null),
                false
        );
        when(productReadService.getProductsByCategory(
                "Gift-Cards",
                10,
                cursorId,
                cursorAverageRating,
                null,
                true,
                false
        )).thenReturn(response);

        // When: category cursor parameter를 포함해 목록 API를 호출한다
        mockMvc.perform(get("/api/products/category")
                        .param("mainCategory", "Gift-Cards")
                        .param("size", "10")
                        .param("cursorId", String.valueOf(cursorId))
                        .param("cursorAverageRating", "4.5")
                        .param("averageRating", "true")
                        .param("ratingNumber", "false"))
                .andExpect(status().isOk());

        // Then: controller는 받은 값을 그대로 ProductReadService에 전달해야 한다
        verify(productReadService).getProductsByCategory(
                "Gift-Cards",
                10,
                cursorId,
                cursorAverageRating,
                null,
                true,
                false
        );
    }

    @Test
    @DisplayName("category 조회에서 size가 최대값 100보다 크면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_category_size_is_greater_than_max() throws Exception {
        // Given: 최대 size를 초과한 category 조회 요청

        // When & Then: size 101은 요청을 거절해야 한다
        mockMvc.perform(get("/api/products/category")
                        .param("mainCategory", "HANDMADE_PRODUCTS")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
