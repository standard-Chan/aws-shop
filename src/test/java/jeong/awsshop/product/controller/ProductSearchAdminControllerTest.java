package jeong.awsshop.product.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jeong.awsshop.product.service.search.ProductSearchReindexService;
import jeong.awsshop.product.service.search.dto.ProductSearchReindexResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductSearchAdminController.class)
class ProductSearchAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductSearchReindexService productSearchReindexService;

    @Test
    @DisplayName("상품 검색 재색인은 기본 pageSize 500으로 service를 호출해야 한다")
    void should_use_default_page_size_when_reindex_page_size_is_omitted() throws Exception {
        // Given
        when(productSearchReindexService.reindexAll(500))
                .thenReturn(new ProductSearchReindexResponse(10, 0, 100));

        // When & Then
        mockMvc.perform(post("/api/admin/products/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexedCount").value(10))
                .andExpect(jsonPath("$.failedCount").value(0));

        verify(productSearchReindexService).reindexAll(500);
    }

    @Test
    @DisplayName("상품 검색 재색인 pageSize가 범위를 벗어나면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_reindex_page_size_is_out_of_range() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/admin/products/search/reindex")
                        .param("pageSize", "5001"))
                .andExpect(status().isBadRequest());
    }
}
