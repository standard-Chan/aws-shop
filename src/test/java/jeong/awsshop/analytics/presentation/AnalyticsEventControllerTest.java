package jeong.awsshop.analytics.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jeong.awsshop.analytics.application.AnalyticsEventService;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.exception.AnalyticsEventPublishException;
import jeong.awsshop.analytics.presentation.dto.AnalyticsEventResponse;
import jeong.awsshop.analytics.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.analytics.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.analytics.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.analytics.presentation.dto.SearchEventRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsEventController.class)
class AnalyticsEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsEventService analyticsEventService;

    @Test
    @DisplayName("검색 이벤트 수집 요청이 정상적이면 202와 eventId/eventType을 반환해야 한다")
    void should_accept_search_event() throws Exception {
        when(analyticsEventService.recordSearch(any(SearchEventRequest.class)))
                .thenReturn(new AnalyticsEventResponse(100L, AnalyticsEventType.SEARCH));

        mockMvc.perform(post("/api/analytics/events/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "keyword": "macbook"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(100L))
                .andExpect(jsonPath("$.eventType").value("SEARCH"));
    }

    @Test
    @DisplayName("상품 조회 이벤트 수집 요청이 정상적이면 202와 eventId/eventType을 반환해야 한다")
    void should_accept_product_view_event() throws Exception {
        when(analyticsEventService.recordProductView(any(ProductViewEventRequest.class)))
                .thenReturn(new AnalyticsEventResponse(101L, AnalyticsEventType.PRODUCT_VIEW));

        mockMvc.perform(post("/api/analytics/events/product-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "productId": 100
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(101L))
                .andExpect(jsonPath("$.eventType").value("PRODUCT_VIEW"));
    }

    @Test
    @DisplayName("상품 조회 이벤트는 searchEventId와 searchKeyword가 있으면 함께 받을 수 있어야 한다")
    void should_accept_product_view_event_with_search_context() throws Exception {
        when(analyticsEventService.recordProductView(any(ProductViewEventRequest.class)))
                .thenReturn(new AnalyticsEventResponse(101L, AnalyticsEventType.PRODUCT_VIEW));

        mockMvc.perform(post("/api/analytics/events/product-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "productId": 100,
                                  "searchEventId": 50,
                                  "searchKeyword": "macbook"
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<ProductViewEventRequest> requestCaptor = ArgumentCaptor.forClass(ProductViewEventRequest.class);
        verify(analyticsEventService).recordProductView(requestCaptor.capture());
        ProductViewEventRequest request = requestCaptor.getValue();
        assertThat(request.searchEventId()).isEqualTo(50L);
        assertThat(request.searchKeyword()).isEqualTo("macbook");
    }

    @Test
    @DisplayName("상품 조회 이벤트의 searchEventId가 0 이하이면 400을 반환해야 한다")
    void should_return_bad_request_when_search_event_id_is_not_positive() throws Exception {
        mockMvc.perform(post("/api/analytics/events/product-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "productId": 100,
                                  "searchEventId": 0,
                                  "searchKeyword": "macbook"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 조회 이벤트의 searchKeyword가 blank이면 400을 반환해야 한다")
    void should_return_bad_request_when_search_keyword_is_blank() throws Exception {
        mockMvc.perform(post("/api/analytics/events/product-view")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "productId": 100,
                                  "searchKeyword": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장바구니 이벤트 수집 요청이 정상적이면 202와 eventId/eventType을 반환해야 한다")
    void should_accept_add_to_cart_event() throws Exception {
        when(analyticsEventService.recordAddToCart(any(AddToCartEventRequest.class)))
                .thenReturn(new AnalyticsEventResponse(102L, AnalyticsEventType.ADD_TO_CART));

        mockMvc.perform(post("/api/analytics/events/add-to-cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "productId": 100
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(102L))
                .andExpect(jsonPath("$.eventType").value("ADD_TO_CART"));
    }

    @Test
    @DisplayName("구매 이벤트 수집 요청이 정상적이면 202와 eventId/eventType을 반환해야 한다")
    void should_accept_purchase_event() throws Exception {
        when(analyticsEventService.recordPurchase(any(PurchaseEventRequest.class)))
                .thenReturn(new AnalyticsEventResponse(103L, AnalyticsEventType.PURCHASE));

        mockMvc.perform(post("/api/analytics/events/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "orderId": 500
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(103L))
                .andExpect(jsonPath("$.eventType").value("PURCHASE"));
    }

    @Test
    @DisplayName("이벤트 요청 값이 유효하지 않으면 400을 반환해야 한다")
    void should_return_bad_request_when_event_request_is_invalid() throws Exception {
        mockMvc.perform(post("/api/analytics/events/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 0,
                                  "keyword": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 503을 반환해야 한다")
    void should_return_service_unavailable_when_publish_fails() throws Exception {
        when(analyticsEventService.recordPurchase(any(PurchaseEventRequest.class)))
                .thenThrow(new AnalyticsEventPublishException("failed", new RuntimeException("kafka down")));

        mockMvc.perform(post("/api/analytics/events/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "orderId": 500
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
