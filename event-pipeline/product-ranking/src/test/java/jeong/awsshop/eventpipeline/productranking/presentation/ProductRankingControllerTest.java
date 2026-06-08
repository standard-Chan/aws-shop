package jeong.awsshop.eventpipeline.productranking.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.productranking.application.ProductRankingService;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductRankingController.class)
class ProductRankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductRankingService productRankingService;

    @Test
    @DisplayName("사용자 행동 이벤트 메시지를 REST로 받아 랭킹에 반영해야 한다")
    void should_record_event() throws Exception {
        mockMvc.perform(post("/api/event-pipeline/product-ranking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 1,
                                  "eventType": "PRODUCT_VIEW",
                                  "userId": 10,
                                  "occurredAt": "2026-06-07T06:00:00Z",
                                  "keyword": null,
                                  "productId": 100,
                                  "orderId": null,
                                  "searchEventId": null
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(productRankingService).record(any(UserBehaviorEventMessage.class));
    }

    @Test
    @DisplayName("실시간 상품 랭킹을 조회해야 한다")
    void should_get_product_rankings() throws Exception {
        when(productRankingService.findTop(2))
                .thenReturn(List.of(
                        new ProductRankingItem(1L, 100L, 14L),
                        new ProductRankingItem(2L, 200L, 3L)
                ));

        mockMvc.perform(get("/api/event-pipeline/product-ranking/rankings")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1L))
                .andExpect(jsonPath("$[0].productId").value(100L))
                .andExpect(jsonPath("$[0].score").value(14L))
                .andExpect(jsonPath("$[1].rank").value(2L))
                .andExpect(jsonPath("$[1].productId").value(200L))
                .andExpect(jsonPath("$[1].score").value(3L));

        verify(productRankingService).findTop(2);
    }
}
