package jeong.awsshop.product.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jeong.awsshop.product.service.search.ProductSearchService;
import jeong.awsshop.product.service.search.dto.ProductSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductSearchController.class)
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductSearchService productSearchService;

    @Test
    @DisplayName("ES 상품 검색은 keyword와 기본 size를 service에 전달해야 한다")
    void should_pass_keyword_and_default_size_to_search_service() throws Exception {
        // Given
        when(productSearchService.search("wire", 20, null, null, null))
                .thenReturn(new ProductSearchResponse(List.of(), null, false));

        // When & Then
        mockMvc.perform(get("/api/products/search")
                        .param("keyword", "wire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(productSearchService).search("wire", 20, null, null, null);
    }

    @Test
    @DisplayName("ES 상품 검색은 sort, order, cursor를 service에 전달해야 한다")
    void should_pass_sort_order_and_cursor_to_search_service() throws Exception {
        // Given
        when(productSearchService.search("wire", 10, "price", "asc", "cursor-token"))
                .thenReturn(new ProductSearchResponse(List.of(), "next-token", true));

        // When & Then
        mockMvc.perform(get("/api/products/search")
                        .param("keyword", "wire")
                        .param("size", "10")
                        .param("sort", "price")
                        .param("order", "asc")
                        .param("cursor", "cursor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value("next-token"))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(productSearchService).search("wire", 10, "price", "asc", "cursor-token");
    }

    @Test
    @DisplayName("ES 상품 검색에서 keyword가 없으면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_keyword_is_missing() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ES 상품 검색에서 size가 범위를 벗어나면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_size_is_out_of_range() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/products/search")
                        .param("keyword", "wire")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
