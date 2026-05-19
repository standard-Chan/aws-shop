package jeong.awsshop.product.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jeong.awsshop.product.controller.ProductController;
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
class GetProductsByKeywordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductReadService productReadService;

    @Test
    @DisplayName("keyword 조회에서 size를 생략하면 기본 size 20을 사용해야 한다")
    void should_use_default_size_when_keyword_size_is_omitted() throws Exception {
        // Given: 빈 검색 결과를 반환하는 keyword 조회 응답을 준비한다.
        when(productReadService.getProductsByKeyword(
                "wire",
                20,
                null,
                null,
                null
        )).thenReturn(new ProductCategoryCursorResponse(List.of(), null, false));

        // When: size 없이 keyword 목록 API를 호출한다.
        mockMvc.perform(get("/api/products/keyword")
                        .param("keyword", "wire"))
                .andExpect(status().isOk());

        // Then: 기본 size 20과 요청 parameter가 그대로 전달되어야 한다.
        verify(productReadService).getProductsByKeyword(
                "wire",
                20,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("keyword 조회 요청이면 keyword, cursor, sort, order parameter를 전달해야 한다")
    void should_pass_keyword_cursor_and_sort_parameters_when_keyword_request_is_valid() throws Exception {
        // Given: 정렬과 cursor가 포함된 keyword 조회 응답을 준비한다.
        Long cursorId = 9_000_000_000_000L;
        ProductCategoryCursorResponse response = new ProductCategoryCursorResponse(
                List.of(),
                new CategoryCursor(cursorId, null, null, null),
                false
        );
        when(productReadService.getProductsByKeyword(
                "wire",
                10,
                cursorId,
                "price",
                "asc"
        )).thenReturn(response);

        // When: keyword, cursor, sort, order를 포함한 요청을 보낸다.
        mockMvc.perform(get("/api/products/keyword")
                        .param("keyword", "wire")
                        .param("size", "10")
                        .param("cursorId", String.valueOf(cursorId))
                        .param("sort", "price")
                        .param("order", "asc"))
                .andExpect(status().isOk());

        // Then: 요청 parameter가 검색 조회 흐름에 그대로 전달되어야 한다.
        verify(productReadService).getProductsByKeyword(
                "wire",
                10,
                cursorId,
                "price",
                "asc"
        );
    }

    @Test
    @DisplayName("keyword 조회에서 size가 범위를 벗어나면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_keyword_size_is_out_of_range() throws Exception {
        // Given: 최대 size를 초과한 keyword 조회 요청이다.

        // When: size 101로 keyword 목록 API를 호출한다.
        // Then: validation 오류로 요청을 거절해야 한다.
        mockMvc.perform(get("/api/products/keyword")
                        .param("keyword", "wire")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("keyword가 누락되면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_keyword_is_missing() throws Exception {
        // Given: 필수 keyword 없이 검색 요청을 보낸다.

        // When: keyword 없이 keyword 목록 API를 호출한다.
        // Then: 필수 query parameter 누락으로 요청을 거절해야 한다.
        mockMvc.perform(get("/api/products/keyword"))
                .andExpect(status().isBadRequest());
    }
}
