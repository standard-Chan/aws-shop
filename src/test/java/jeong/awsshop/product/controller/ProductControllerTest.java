package jeong.awsshop.product.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductImageResponse;
import jeong.awsshop.product.service.productread.dto.ProductSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductReadService productReadService;

    @Test
    @DisplayName("size를 생략하면 기본 size 20과 cursor null로 service를 호출해야 한다")
    void should_call_service_with_default_size_when_size_is_omitted() throws Exception {
        // Given: service가 빈 cursor 응답을 반환하도록 준비한다
        when(productReadService.getProducts(20, null))
                .thenReturn(new ProductCursorResponse(List.of(), null, false));

        // When: size 없이 목록 API를 호출한다
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        // Then: controller는 기본 size 20을 service에 전달해야 한다
        verify(productReadService).getProducts(20, null);
    }

    @Test
    @DisplayName("size와 cursor가 있으면 query parameter를 service에 그대로 전달해야 한다")
    void should_call_service_with_size_and_cursor_when_query_parameters_exist() throws Exception {
        // Given: snowflake cursor와 service 응답을 준비한다
        Long cursor = 9_000_000_000_000L;
        when(productReadService.getProducts(10, cursor))
                .thenReturn(new ProductCursorResponse(List.of(), null, false));

        // When: size와 cursor를 포함해 목록 API를 호출한다
        mockMvc.perform(get("/api/products")
                        .param("size", "10")
                        .param("cursor", String.valueOf(cursor)))
                .andExpect(status().isOk());

        // Then: query parameter가 service에 그대로 전달되어야 한다
        verify(productReadService).getProducts(10, cursor);
    }

    @Test
    @DisplayName("유효한 요청이면 cursor response를 HTTP 200 JSON으로 반환해야 한다")
    void should_return_ok_with_cursor_response_when_request_is_valid() throws Exception {
        // Given: 대표 image를 포함한 service 응답
        ProductImageResponse image = new ProductImageResponse(
                "MAIN",
                "main-thumb",
                "main-large",
                "main-hires"
        );
        ProductSummaryResponse product = new ProductSummaryResponse(
                101L,
                "B07NTK7T5P",
                "Daisy Keychain Wristlet Gray Fabric Key fob Lanyard",
                MainCategory.HANDMADE_PRODUCTS,
                new BigDecimal("4.5"),
                12,
                null,
                "Generic",
                image
        );
        ProductCursorResponse response = new ProductCursorResponse(List.of(product), 101L, true);
        when(productReadService.getProducts(1, null)).thenReturn(response);

        // When & Then: controller는 service 응답을 JSON으로 반환해야 한다
        mockMvc.perform(get("/api/products").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].id").value(101L))
                .andExpect(jsonPath("$.products[0].parentAsin").value("B07NTK7T5P"))
                .andExpect(jsonPath("$.products[0].image.variant").value("MAIN"))
                .andExpect(jsonPath("$.nextCursorId").value(101L))
                .andExpect(jsonPath("$.hasNext").value(true));
    }
}
