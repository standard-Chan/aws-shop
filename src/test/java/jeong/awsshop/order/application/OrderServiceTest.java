package jeong.awsshop.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderRepository;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.order.exception.OrderAlreadyCanceledException;
import jeong.awsshop.order.exception.OrderAlreadyCompletedException;
import jeong.awsshop.order.exception.OrderAlreadyExecutingException;
import jeong.awsshop.order.exception.OrderExpiredException;
import jeong.awsshop.order.exception.OrderInvalidStatusTransitionException;
import jeong.awsshop.order.exception.OrderNotFoundException;
import jeong.awsshop.order.presentation.dto.OrderSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository);
    }

    @Test
    @DisplayName("주문 생성 요청이 오면 임시 주문 정보를 저장하고 반환해야 한다")
    void should_create_temporary_order_when_create_order_is_called() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            return Order.builder()
                .id(10L)
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .completedAt(order.getCompletedAt())
                .build();
        });

        OrderSummaryResponse response = orderService.createOrder();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getUserId()).isEqualTo(1L);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.NOT_STARTED);
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("129.99");
        assertThat(savedOrder.getShippingAddress()).isEqualTo("Seoul Songpa-gu Olympic-ro 300");
        assertThat(savedOrder.getCreatedAt()).isNotNull();
        assertThat(savedOrder.getExpiresAt()).isAfter(savedOrder.getCreatedAt());
        assertThat(savedOrder.getCompletedAt()).isNull();

        assertThat(response.orderId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderStatus.NOT_STARTED);
        assertThat(response.totalAmount()).isEqualByComparingTo("129.99");
        assertThat(response.shippingAddress()).isEqualTo("Seoul Songpa-gu Olympic-ro 300");
    }

    @Test
    @DisplayName("주문 id로 조회하면 저장된 주문 정보를 반환해야 한다")
    void should_return_order_summary_when_order_exists() {
        when(orderRepository.findById(3L)).thenReturn(Optional.of(
            Order.builder()
                .id(3L)
                .userId(9L)
                .status(OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("55.00"))
                .shippingAddress("Busan Haeundae-gu Centum nam-daero 35")
                .build()
        ));

        OrderSummaryResponse response = orderService.getOrder(3L);

        assertThat(response.orderId()).isEqualTo(3L);
        assertThat(response.userId()).isEqualTo(9L);
        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.totalAmount()).isEqualByComparingTo("55.00");
        assertThat(response.shippingAddress()).isEqualTo("Busan Haeundae-gu Centum nam-daero 35");
    }

    @Test
    @DisplayName("존재하지 않는 주문을 조회하면 예외를 던져야 한다")
    void should_throw_exception_when_order_does_not_exist() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessage("[Order] Order not found with id: 99");
    }

    @Test
    @DisplayName("주문 executing 갱신 요청이 오면 주문 상태를 EXECUTING으로 변경해야 한다")
    void should_execute_order_when_executing_order_is_called() {
        when(orderRepository.updateStatusToExecutingIfAvailable(7L)).thenReturn(1);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(
            Order.builder()
                .id(7L)
                .userId(1L)
                .status(OrderStatus.EXECUTING)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        OrderSummaryResponse response = orderService.executingOrder(7L);

        assertThat(response.orderId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(OrderStatus.EXECUTING);
    }

    @Test
    @DisplayName("완료된 주문은 executing 상태로 변경할 수 없어야 한다")
    void should_throw_exception_when_completed_order_is_updated_to_executing() {
        when(orderRepository.updateStatusToExecutingIfAvailable(8L)).thenReturn(0);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(
            Order.builder()
                .id(8L)
                .userId(1L)
                .status(OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress("Seoul")
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build()
        ));

        assertThatThrownBy(() -> orderService.executingOrder(8L))
            .isInstanceOf(OrderAlreadyCompletedException.class)
            .hasMessage("[Order] Completed order cannot be updated to EXECUTING. id=8");
    }

    @Test
    @DisplayName("취소된 주문은 executing 상태로 변경할 수 없어야 한다")
    void should_throw_exception_when_canceled_order_is_updated_to_executing() {
        when(orderRepository.updateStatusToExecutingIfAvailable(13L)).thenReturn(0);
        when(orderRepository.findById(13L)).thenReturn(Optional.of(
            Order.builder()
                .id(13L)
                .userId(1L)
                .status(OrderStatus.CANCELED)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        assertThatThrownBy(() -> orderService.executingOrder(13L))
            .isInstanceOf(OrderAlreadyCanceledException.class)
            .hasMessage("[Order] CANCELED order cannot be updated. id=13");
    }

    @Test
    @DisplayName("만료된 주문은 executing 상태로 변경할 수 없어야 한다")
    void should_throw_exception_when_expired_order_is_updated_to_executing() {
        when(orderRepository.updateStatusToExecutingIfAvailable(14L)).thenReturn(0);
        when(orderRepository.findById(14L)).thenReturn(Optional.of(
            Order.builder()
                .id(14L)
                .userId(1L)
                .status(OrderStatus.EXPIRED)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        assertThatThrownBy(() -> orderService.executingOrder(14L))
            .isInstanceOf(OrderExpiredException.class)
            .hasMessage("[Order] EXPIRED order cannot be updated. id=14");
    }

    @Test
    @DisplayName("이미 처리 중인 주문은 다시 executing 상태로 변경할 수 없어야 한다")
    void should_throw_exception_when_order_is_already_executing() {
        when(orderRepository.updateStatusToExecutingIfAvailable(9L)).thenReturn(0);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(
            Order.builder()
                .id(9L)
                .userId(1L)
                .status(OrderStatus.EXECUTING)
                .totalAmount(new BigDecimal("50.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        assertThatThrownBy(() -> orderService.executingOrder(9L))
            .isInstanceOf(OrderAlreadyExecutingException.class)
            .hasMessage("[Order] Order is already EXECUTING. id=9");
    }

    @Test
    @DisplayName("결제 처리 실패 후 pending 갱신 요청이 오면 주문 상태를 PENDING으로 변경해야 한다")
    void should_pending_order_when_pending_order_is_called() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(
            Order.builder()
                .id(10L)
                .userId(1L)
                .status(OrderStatus.NOT_STARTED)
                .totalAmount(new BigDecimal("25.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        OrderSummaryResponse response = orderService.pendingOrder(10L);

        assertThat(response.orderId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 성공 갱신 요청이 오면 주문 상태를 COMPLETED로 변경해야 한다")
    void should_complete_order_when_complete_order_is_called() {
        when(orderRepository.findById(11L)).thenReturn(Optional.of(
            Order.builder()
                .id(11L)
                .userId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("30.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        OrderSummaryResponse response = orderService.completeOrder(11L);

        assertThat(response.orderId()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("주문 실패 갱신 요청이 오면 주문 상태를 CANCELED로 변경해야 한다")
    void should_cancel_order_when_cancel_order_is_called() {
        when(orderRepository.findById(12L)).thenReturn(Optional.of(
            Order.builder()
                .id(12L)
                .userId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("40.00"))
                .shippingAddress("Busan")
                .build()
        ));

        OrderSummaryResponse response = orderService.cancelOrder(12L);

        assertThat(response.orderId()).isEqualTo(12L);
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("완료된 주문은 다른 상태로 전이할 수 없어야 한다")
    void should_reject_transition_from_completed_order() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(
            Order.builder()
                .id(21L)
                .userId(1L)
                .status(OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("30.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        assertThatThrownBy(() -> orderService.pendingOrder(21L))
            .isInstanceOf(OrderInvalidStatusTransitionException.class)
            .hasMessageContaining("id=21");
    }

    @Test
    @DisplayName("취소된 주문은 다른 상태로 전이할 수 없어야 한다")
    void should_reject_transition_from_canceled_order() {
        when(orderRepository.findById(22L)).thenReturn(Optional.of(
            Order.builder()
                .id(22L)
                .userId(1L)
                .status(OrderStatus.CANCELED)
                .totalAmount(new BigDecimal("30.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        assertThatThrownBy(() -> orderService.completeOrder(22L))
            .isInstanceOf(OrderInvalidStatusTransitionException.class)
            .hasMessageContaining("id=22");
    }

    @Test
    @DisplayName("만료된 주문은 다른 상태로 전이할 수 없어야 한다")
    void should_reject_transition_from_expired_order() {
        when(orderRepository.findById(23L)).thenReturn(Optional.of(
            Order.builder()
                .id(23L)
                .userId(1L)
                .status(OrderStatus.EXPIRED)
                .totalAmount(new BigDecimal("30.00"))
                .shippingAddress("Seoul")
                .build()
        ));

        assertThatThrownBy(() -> orderService.cancelOrder(23L))
            .isInstanceOf(OrderInvalidStatusTransitionException.class)
            .hasMessageContaining("id=23");
    }
}
