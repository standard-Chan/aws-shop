package jeong.awsshop.analytics.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jeong.awsshop.analytics.application.AnalyticsBatchEventService;
import jeong.awsshop.analytics.exception.AnalyticsEventPublishException;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsBatchEventController.class)
class AnalyticsBatchEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsBatchEventService analyticsBatchEventService;

    @Test
    @DisplayName("batch 이벤트 요청이 정상적이면 202와 acceptedCount를 반환해야 한다")
    void should_accept_batch_events() throws Exception {
        when(analyticsBatchEventService.recordBatch(any(AnalyticsBatchEventRequest.class)))
                .thenReturn(new AnalyticsBatchEventResponse(4));

        mockMvc.perform(post("/api/analytics/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {"eventType": "SEARCH", "userId": 1, "keyword": "macbook"},
                                    {"eventType": "PRODUCT_VIEW", "userId": 1, "productId": 100, "searchEventId": 10, "keyword": "macbook"},
                                    {"eventType": "ADD_TO_CART", "userId": 1, "productId": 100},
                                    {"eventType": "PURCHASE", "userId": 1, "orderId": 500}
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedCount").value(4));
    }

    @Test
    @DisplayName("batch 이벤트 요청 값이 유효하지 않으면 400을 반환해야 한다")
    void should_return_bad_request_when_batch_event_is_invalid() throws Exception {
        mockMvc.perform(post("/api/analytics/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {"eventType": "SEARCH", "userId": 1, "keyword": ""},
                                    {"eventType": "PRODUCT_VIEW", "userId": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("batch Kafka 발행 실패 시 503을 반환해야 한다")
    void should_return_service_unavailable_when_batch_publish_fails() throws Exception {
        when(analyticsBatchEventService.recordBatch(any(AnalyticsBatchEventRequest.class)))
                .thenThrow(new AnalyticsEventPublishException("failed", new RuntimeException("kafka down")));

        mockMvc.perform(post("/api/analytics/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "events": [
                                    {"eventType": "PURCHASE", "userId": 1, "orderId": 500}
                                  ]
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
