package jeong.awsshop.order.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import jeong.awsshop.order.application.OrderService;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.order.exception.OrderAlreadyCanceledException;
import jeong.awsshop.order.exception.OrderAlreadyExecutingException;
import jeong.awsshop.order.exception.OrderExpiredException;
import jeong.awsshop.order.exception.OrderNotFoundException;
import jeong.awsshop.order.presentation.dto.OrderSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("주문 생성 요청이 오면 service 결과를 JSON으로 반환해야 한다")
    void should_return_created_order_when_create_order_request_is_valid() throws Exception {
        OrderSummaryResponse response = new OrderSummaryResponse(
            1L,
            1L,
            OrderStatus.NOT_STARTED,
            new BigDecimal("129.99"),
            "Seoul Songpa-gu Olympic-ro 300"
        );
        when(orderService.createOrder()).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(1L))
            .andExpect(jsonPath("$.userId").value(1L))
            .andExpect(jsonPath("$.status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.totalAmount").value(129.99))
            .andExpect(jsonPath("$.shippingAddress").value("Seoul Songpa-gu Olympic-ro 300"));

        verify(orderService).createOrder();
    }

    @Test
    @DisplayName("주문 id로 조회하면 service 결과를 JSON으로 반환해야 한다")
    void should_return_order_when_get_order_request_is_valid() throws Exception {
        OrderSummaryResponse response = new OrderSummaryResponse(
            3L,
            9L,
            OrderStatus.COMPLETED,
            new BigDecimal("55.00"),
            "Busan Haeundae-gu Centum nam-daero 35"
        );
        when(orderService.getOrder(3L)).thenReturn(response);

        mockMvc.perform(get("/api/orders/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(3L))
            .andExpect(jsonPath("$.userId").value(9L))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalAmount").value(55.00))
            .andExpect(jsonPath("$.shippingAddress").value("Busan Haeundae-gu Centum nam-daero 35"));

        verify(orderService).getOrder(3L);
    }

    @Test
    @DisplayName("주문 executing 상태 갱신 요청이 오면 service 결과를 JSON으로 반환해야 한다")
    void should_return_executing_order_when_executing_order_request_is_valid() throws Exception {
        OrderSummaryResponse response = new OrderSummaryResponse(
            4L,
            1L,
            OrderStatus.EXECUTING,
            new BigDecimal("66.00"),
            "Seoul"
        );
        when(orderService.executingOrder(4L)).thenReturn(response);

        mockMvc.perform(post("/api/orders/4/executing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(4L))
            .andExpect(jsonPath("$.status").value("EXECUTING"));

        verify(orderService).executingOrder(4L);
    }

    @Test
    @DisplayName("이미 처리 중인 주문의 executing 상태 갱신 요청은 409를 반환해야 한다")
    void should_return_conflict_when_executing_order_request_is_invalid() throws Exception {
        when(orderService.executingOrder(4L))
            .thenThrow(new OrderAlreadyExecutingException(4L));

        mockMvc.perform(post("/api/orders/4/executing"))
            .andExpect(status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(OrderController.ORDER_STATUS_HEADER, "EXECUTING"))
            .andExpect(content().string("[Order] Order is already EXECUTING. id=4"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 executing 상태 갱신 요청은 404를 반환해야 한다")
    void should_return_not_found_when_order_does_not_exist_for_executing_request() throws Exception {
        when(orderService.executingOrder(99L))
            .thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(post("/api/orders/99/executing"))
            .andExpect(status().isNotFound())
            .andExpect(content().string("[Order] Order not found with id: 99"));
    }

    @Test
    @DisplayName("완료된 주문의 executing 상태 갱신 요청은 409를 반환해야 한다")
    void should_return_conflict_when_completed_order_is_rejected_for_executing_request() throws Exception {
        when(orderService.executingOrder(5L))
            .thenThrow(new jeong.awsshop.order.exception.OrderAlreadyCompletedException(5L));

        mockMvc.perform(post("/api/orders/5/executing"))
            .andExpect(status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(OrderController.ORDER_STATUS_HEADER, "COMPLETED"))
            .andExpect(content().string("[Order] Completed order cannot be updated to EXECUTING. id=5"));
    }

    @Test
    @DisplayName("취소된 주문의 executing 상태 갱신 요청은 409를 반환해야 한다")
    void should_return_conflict_when_canceled_order_is_rejected_for_executing_request() throws Exception {
        when(orderService.executingOrder(6L))
            .thenThrow(new OrderAlreadyCanceledException(6L));

        mockMvc.perform(post("/api/orders/6/executing"))
            .andExpect(status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(OrderController.ORDER_STATUS_HEADER, "CANCELED"))
            .andExpect(content().string("[Order] CANCELED order cannot be updated. id=6"));
    }

    @Test
    @DisplayName("만료된 주문의 executing 상태 갱신 요청은 410을 반환해야 한다")
    void should_return_gone_when_expired_order_is_rejected_for_executing_request() throws Exception {
        when(orderService.executingOrder(7L))
            .thenThrow(new OrderExpiredException(7L));

        mockMvc.perform(post("/api/orders/7/executing"))
            .andExpect(status().isGone())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(OrderController.ORDER_STATUS_HEADER, "EXPIRED"))
            .andExpect(content().string("[Order] EXPIRED order cannot be updated. id=7"));
    }

    @Test
    @DisplayName("결제 처리 실패 후 pending 상태 갱신 요청이 오면 service 결과를 JSON으로 반환해야 한다")
    void should_return_pending_order_when_pending_order_request_is_valid() throws Exception {
        OrderSummaryResponse response = new OrderSummaryResponse(
            4L,
            1L,
            OrderStatus.PENDING,
            new BigDecimal("66.00"),
            "Seoul"
        );
        when(orderService.pendingOrder(4L)).thenReturn(response);

        mockMvc.perform(post("/api/orders/4/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(4L))
            .andExpect(jsonPath("$.status").value("PENDING"));

        verify(orderService).pendingOrder(4L);
    }

    @Test
    @DisplayName("주문 성공 상태 갱신 요청이 오면 service 결과를 JSON으로 반환해야 한다")
    void should_return_completed_order_when_complete_order_request_is_valid() throws Exception {
        OrderSummaryResponse response = new OrderSummaryResponse(
            5L,
            1L,
            OrderStatus.COMPLETED,
            new BigDecimal("77.00"),
            "Incheon"
        );
        when(orderService.completeOrder(5L)).thenReturn(response);

        mockMvc.perform(post("/api/orders/5/success"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(5L))
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(orderService).completeOrder(5L);
    }

    @Test
    @DisplayName("주문 실패 상태 갱신 요청이 오면 service 결과를 JSON으로 반환해야 한다")
    void should_return_canceled_order_when_cancel_order_request_is_valid() throws Exception {
        OrderSummaryResponse response = new OrderSummaryResponse(
            6L,
            1L,
            OrderStatus.CANCELED,
            new BigDecimal("88.00"),
            "Daegu"
        );
        when(orderService.cancelOrder(6L)).thenReturn(response);

        mockMvc.perform(post("/api/orders/6/fail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(6L))
            .andExpect(jsonPath("$.status").value("CANCELED"));

        verify(orderService).cancelOrder(6L);
    }
}
