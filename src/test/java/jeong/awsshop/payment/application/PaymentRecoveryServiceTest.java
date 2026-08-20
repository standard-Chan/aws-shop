package jeong.awsshop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.stock.application.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private StockService stockService;

    private PaymentRecoveryService paymentRecoveryService;

    @BeforeEach
    void setUp() {
        paymentRecoveryService = new PaymentRecoveryService(
            paymentRepository,
            tossPaymentClient,
            orderClient,
            stockService
        );
    }

    @Test
    @DisplayName("서버 시작 시점 이전에 생성된 EXECUTING 결제만 복구 대상으로 조회해야 한다")
    void should_find_executing_payments_created_before_application_startup_time() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of());

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        verify(paymentRepository).findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        );
    }

    @Test
    @DisplayName("Toss 결제가 DONE이면 내부 결제와 주문을 성공 상태로 복구해야 한다")
    void should_recover_payment_and_order_as_success_when_toss_payment_is_done() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, "payment-key-1");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));
        when(tossPaymentClient.getPayment("payment-key-1")).thenReturn(tossPayment("DONE"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getCompletedAt()).isNotNull();
        verify(orderClient).updateCompleteOrder(123L);
        verify(paymentRepository).save(payment);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(stockService, never()).increase(10L, 2);
        verify(stockService, never()).increase(20L, 1);
    }

    @Test
    @DisplayName("Toss 결제가 DONE이 아니면 결제 실패 처리 후 주문과 재고를 복구해야 한다")
    void should_fail_payment_and_restore_order_and_stock_when_toss_payment_is_not_done() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, "payment-key-1");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));
        when(tossPaymentClient.getPayment("payment-key-1")).thenReturn(tossPayment("ABORTED"));
        when(orderClient.getOrder(123L)).thenReturn(orderWithItems());

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(orderClient).updatePendingOrder(123L);
        verify(orderClient).getOrder(123L);
        verify(stockService).increase(20L, 1);
        verify(stockService).increase(10L, 2);
        verify(orderClient, never()).updateCompleteOrder(123L);
    }

    @Test
    @DisplayName("paymentKey가 없으면 Toss 조회 없이 복구를 건너뛰어야 한다")
    void should_skip_recovery_when_payment_key_is_missing() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, null);
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
        verify(tossPaymentClient, never()).getPayment(null);
        verify(paymentRepository, never()).save(payment);
        verify(orderClient, never()).updateCompleteOrder(123L);
        verify(orderClient, never()).updatePendingOrder(123L);
    }

    @Test
    @DisplayName("한 결제 복구가 실패해도 다음 결제 복구를 계속해야 한다")
    void should_continue_recovering_next_payment_when_one_payment_recovery_fails() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment failedToRecover = executingPayment(1L, 123L, "payment-key-1");
        Payment recoverable = executingPayment(2L, 456L, "payment-key-2");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(failedToRecover, recoverable));
        when(tossPaymentClient.getPayment("payment-key-1")).thenThrow(new RuntimeException("toss lookup failed"));
        when(tossPaymentClient.getPayment("payment-key-2")).thenReturn(tossPayment("DONE"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(failedToRecover.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(recoverable.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderClient).updateCompleteOrder(456L);
        verify(paymentRepository).save(recoverable);
    }

    private Payment executingPayment(Long paymentId, Long orderId, String paymentKey) {
        return Payment.builder()
            .id(paymentId)
            .orderId(orderId)
            .paymentKey(paymentKey)
            .status(PaymentStatus.EXECUTING)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(10))
            .expiresAt(LocalDateTime.now().minusMinutes(5))
            .build();
    }

    private TossPaymentConfirmResponse tossPayment(String status) {
        return new TossPaymentConfirmResponse(
            "payment-key-1",
            "1",
            "CARD",
            status,
            140000L,
            OffsetDateTime.parse("2026-08-17T09:50:00+09:00"),
            OffsetDateTime.parse("2026-08-17T09:51:00+09:00")
        );
    }

    private OrderSummary orderWithItems() {
        return new OrderSummary(
            123L,
            1L,
            OrderStatus.EXECUTING,
            new BigDecimal("100.00"),
            "Seoul",
            List.of(
                new OrderLineSummary(10L, 2, new BigDecimal("30.00"), new BigDecimal("60.00")),
                new OrderLineSummary(20L, 1, new BigDecimal("40.00"), new BigDecimal("40.00"))
            )
        );
    }
}
