package jeong.awsshop.stock.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jeong.awsshop.stock.application.StockService;
import jeong.awsshop.stock.application.dto.StockResponse;
import jeong.awsshop.stock.exception.InsufficientStockException;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import jeong.awsshop.stock.exception.StockNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockService stockService;

    @Test
    @DisplayName("재고 차감 요청이 정상적이면 service 결과를 JSON으로 반환해야 한다")
    void should_return_stock_response_when_decrease_request_is_valid() throws Exception {
        when(stockService.decrease(10L, 2)).thenReturn(new StockResponse(10L, 8));

        mockMvc.perform(post("/api/stocks/10/decrease")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 2
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(10L))
            .andExpect(jsonPath("$.quantity").value(8));

        verify(stockService).decrease(10L, 2);
    }

    @Test
    @DisplayName("재고 추가 요청이 정상적이면 service 결과를 JSON으로 반환해야 한다")
    void should_return_stock_response_when_increase_request_is_valid() throws Exception {
        when(stockService.increase(10L, 5)).thenReturn(new StockResponse(10L, 15));

        mockMvc.perform(post("/api/stocks/10/increase")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 5
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(10L))
            .andExpect(jsonPath("$.quantity").value(15));

        verify(stockService).increase(10L, 5);
    }

    @Test
    @DisplayName("재고 변경 수량이 유효하지 않으면 HTTP 400을 반환해야 한다")
    void should_return_bad_request_when_quantity_is_invalid() throws Exception {
        when(stockService.decrease(10L, 0)).thenThrow(new InvalidStockQuantityException(0));

        mockMvc.perform(post("/api/stocks/10/decrease")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("[Stock] 재고 변경 수량은 양수여야 합니다. quantity=0"));
    }

    @Test
    @DisplayName("재고가 존재하지 않으면 HTTP 404를 반환해야 한다")
    void should_return_not_found_when_stock_does_not_exist() throws Exception {
        when(stockService.decrease(10L, 2)).thenThrow(new StockNotFoundException(10L));

        mockMvc.perform(post("/api/stocks/10/decrease")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 2
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(content().string("[Stock] 재고가 존재하지 않습니다. productId=10"));
    }

    @Test
    @DisplayName("재고가 부족하면 HTTP 409를 반환해야 한다")
    void should_return_conflict_when_stock_is_insufficient() throws Exception {
        when(stockService.decrease(10L, 5)).thenThrow(new InsufficientStockException(10L, 5, 2));

        mockMvc.perform(post("/api/stocks/10/decrease")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 5
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().string(
                "[Stock] 재고가 부족합니다. productId=10, requestedQuantity=5, currentQuantity=2"
            ));
    }
}
