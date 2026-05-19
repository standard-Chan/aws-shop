package jeong.awsshop.common.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jeong.awsshop.product.controller.ProductController;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(WebCorsConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
class WebCorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductReadService productReadService;

    @Test
    @DisplayName("허용된 origin의 preflight OPTIONS 요청에는 CORS 응답 헤더를 반환해야 한다")
    void should_allow_preflight_request_for_configured_origin() throws Exception {
        when(productReadService.getProducts(20, null))
                .thenReturn(new ProductCursorResponse(List.of(), null, false));

        mockMvc.perform(options("/api/products")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
