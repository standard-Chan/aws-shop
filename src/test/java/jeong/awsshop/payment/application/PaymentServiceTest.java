package jeong.awsshop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentExpiredException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentRecoveryRequiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCanceledException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCompletedException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyExecutingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderExpiredException;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderClient orderClient;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(orderClient, paymentRepository, tossPaymentClient, snowflakeIdGenerator);
    }

    @Test
    @DisplayName("주문 조회가 정상적이면 주문 금액과 초기 상태로 결제를 생성해야 한다")
    void should_create_payment_with_order_amount_and_not_started_status_when_order_exists() {
        // Given
        // 주문 조회 결과와 저장된 결제 엔티티를 준비한다.
        when(orderClient.updateExecutingStatus(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
        when(snowflakeIdGenerator.nextId()).thenReturn(1L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            return Payment.builder()
                .id(1L)
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .createdAt(payment.getCreatedAt())
                .expiresAt(payment.getExpiresAt())
                .completedAt(payment.getCompletedAt())
                .build();
        });

        // When
        // 주문 번호로 결제를 생성한다.
        PaymentResponse response = paymentService.createPayment(new CreatePaymentRequest(123L));

        // Then
        // 저장되는 결제와 반환 응답에 주문 금액과 초기 상태가 반영되어야 한다.
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertThat(savedPayment.getOrderId()).isEqualTo(123L);
        assertThat(savedPayment.getAmount()).isEqualByComparingTo("100.00");
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(savedPayment.getCreatedAt()).isNotNull();
        assertThat(savedPayment.getExpiresAt()).isAfter(savedPayment.getCreatedAt());
        assertThat(savedPayment.getCompletedAt()).isNull();

        assertThat(response.paymentId()).isEqualTo("1");
        assertThat(response.orderId()).isEqualTo(123L);
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.NOT_STARTED);
    }

    @Test
    @DisplayName("주문 조회 예외가 발생하면 저장 없이 예외를 그대로 전파해야 한다")
    void should_propagate_exception_without_saving_when_order_lookup_fails() {
        // Given
        // 주문 조회 클라이언트가 예외를 던지도록 준비한다.
        when(orderClient.updateExecutingStatus(123L)).thenThrow(new RuntimeException("order lookup failed"));

        // When
        // 결제 생성을 실행한다.
        // Then
        // service는 payment 문맥의 예외로 번역하고 저장소는 호출하지 않아야 한다.
        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentOrderLookupException.class)
            .hasMessage("[Payment-Order] Order 서버와의 통신이 실패했습니다. orderId=123")
            .hasCauseInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("order lookup failed");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문이 이미 완료되었으면 결제 생성을 거절해야 한다")
    void should_reject_payment_creation_when_order_is_completed() {
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderAlreadyCompletedException(123L, new RuntimeException("completed")));

        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentOrderAlreadyCompletedException.class)
            .hasMessage("[Payment-Order] 이미 완료된 주문입니다. orderId=123");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문이 이미 취소되었으면 결제 생성을 거절해야 한다")
    void should_reject_payment_creation_when_order_is_canceled() {
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderAlreadyCanceledException(123L, new RuntimeException("canceled")));

        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentOrderAlreadyCanceledException.class)
            .hasMessage("[Payment-Order] 이미 실패한 주문입니다. orderId=123");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문이 만료되었으면 결제 생성을 거절해야 한다")
    void should_reject_payment_creation_when_order_is_expired() {
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderExpiredException(123L, new RuntimeException("expired")));

        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentOrderExpiredException.class)
            .hasMessage("[Payment-Order] 만료된 주문입니다. orderId=123");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문 executing 갱신이 이미 진행 중 예외를 반환하고 활성 결제가 만료되지 않았으면 기존 결제를 반환해야 한다")
    void should_return_existing_active_payment_when_order_is_already_executing() {
        // Given
        when(orderClient.updateExecutingStatus(123L)).thenThrow(
            new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing"))
        );
        Payment activePayment = Payment.builder()
            .id(55L)
            .orderId(123L)
            .status(PaymentStatus.NOT_STARTED)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(1))
            .expiresAt(LocalDateTime.now().plusMinutes(4))
            .build();
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of(activePayment));

        // When
        PaymentResponse response = paymentService.createPayment(new CreatePaymentRequest(123L));

        // Then
        assertThat(response.paymentId()).isEqualTo("55");
        assertThat(response.orderId()).isEqualTo(123L);
        assertThat(response.status()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("활성 결제가 만료되었으면 만료 처리 후 만료 예외를 반환해야 한다")
    void should_expire_existing_payment_and_throw_expired_exception_when_active_payment_is_expired() {
        // Given
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing")))
            ;
        Payment expiredPayment = Payment.builder()
            .id(55L)
            .orderId(123L)
            .status(PaymentStatus.NOT_STARTED)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(10))
            .expiresAt(LocalDateTime.now().minusMinutes(5))
            .build();
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of(expiredPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        // Then
        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentExpiredException.class)
            .hasMessage("[Payment] 결제가 만료되었습니다. orderId=123, paymentId=55");

        assertThat(expiredPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(orderClient, times(1)).updateExecutingStatus(123L);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(paymentRepository, times(1)).save(expiredPayment);
    }

    @Test
    @DisplayName("주문은 executing 이지만 활성 결제가 없으면 강제 복구 후 재시도 예외를 반환해야 한다")
    void should_recover_and_throw_retryable_exception_when_active_payment_is_missing() {
        // Given
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing")))
            ;
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of());

        // When
        // Then
        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentRecoveryRequiredException.class)
            .hasMessage("[Payment] 결제 처리 상태를 복구했습니다. 다시 시도해주세요. orderId=123");

        verify(orderClient).updatePendingOrder(123L);
        verify(orderClient, times(1)).updateExecutingStatus(123L);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("활성 결제가 2개 이상이면 DB 이상 상황으로 예외를 던져야 한다")
    void should_throw_exception_when_multiple_active_payments_exist() {
        // Given
        when(orderClient.updateExecutingStatus(123L)).thenThrow(
            new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing"))
        );
        Payment first = Payment.builder()
            .id(1L)
            .orderId(123L)
            .status(PaymentStatus.NOT_STARTED)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(2))
            .expiresAt(LocalDateTime.now().plusMinutes(3))
            .build();
        Payment second = Payment.builder()
            .id(2L)
            .orderId(123L)
            .status(PaymentStatus.EXECUTING)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(1))
            .expiresAt(LocalDateTime.now().plusMinutes(4))
            .build();
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of(first, second));

        // When, Then
        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(PaymentException.class)
            .hasMessage("[Payment] 처리 중인 결제가 2개 이상 존재합니다. orderId=123");
    }

    @Test
    @DisplayName("저장소 예외가 발생하면 예외를 그대로 전파해야 한다")
    void should_propagate_exception_when_payment_repository_save_fails() {
        // Given
        // 주문 조회는 성공하지만 저장소가 예외를 던지도록 준비한다.
        when(orderClient.updateExecutingStatus(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
        when(snowflakeIdGenerator.nextId()).thenReturn(1L);
        when(paymentRepository.save(any(Payment.class))).thenThrow(new RuntimeException("save failed"));

        // When
        // 결제 생성을 실행한다.
        // Then
        // service는 저장 실패를 숨기지 않고 그대로 전파해야 한다.
        assertThatThrownBy(() -> paymentService.createPayment(new CreatePaymentRequest(123L)))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("save failed");
    }

    private OrderSummary createOrderSummary(Long orderId, BigDecimal totalPrice) {
        return new OrderSummary(
            orderId,
            1L,
            jeong.awsshop.order.domain.OrderStatus.EXECUTING,
            totalPrice,
            "Seoul"
        );
    }
}
