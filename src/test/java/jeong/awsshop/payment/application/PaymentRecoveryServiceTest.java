package jeong.awsshop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.exception.TossPaymentFailureType;
import jeong.awsshop.payment.infrastructure.TossPaymentGateway;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.stock.application.StockReservationService;
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
    private TossPaymentGateway tossPaymentClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private StockReservationService stockReservationService;

    private PaymentRecoveryService paymentRecoveryService;

    @BeforeEach
    void setUp() {
        paymentRecoveryService = new PaymentRecoveryService(
            paymentRepository,
            tossPaymentClient,
            orderClient,
            stockReservationService
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
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(true);
        when(tossPaymentClient.getPayment("payment-key-1")).thenReturn(tossPayment("DONE"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getCompletedAt()).isNotNull();
        verify(orderClient).updateCompleteOrder(123L);
        verify(paymentRepository).save(payment);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(stockReservationService).complete(1L);
        verify(stockReservationService, never()).restore(1L);
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
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(true);
        when(tossPaymentClient.getPayment("payment-key-1")).thenReturn(tossPayment("ABORTED"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(orderClient).updatePendingOrder(123L);
        verify(stockReservationService).restore(1L);
        verify(orderClient, never()).updateCompleteOrder(123L);
    }

    @Test
    @DisplayName("Toss 조회 상태가 미완료이면 같은 멱등키로 confirm을 1회 재시도해야 한다")
    void should_retry_confirm_once_when_toss_lookup_status_is_not_finished() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, "payment-key-1");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(true);
        when(tossPaymentClient.getPayment("payment-key-1")).thenReturn(tossPayment("READY"));
        when(tossPaymentClient.confirm(any(), eq("1"))).thenReturn(tossPayment("DONE"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(tossPaymentClient).confirm(any(), eq("1"));
        verify(orderClient).updateCompleteOrder(123L);
        verify(stockReservationService).complete(1L);
        verify(stockReservationService, never()).restore(1L);
    }

    @Test
    @DisplayName("Toss 조회 결과 결제 기록이 없으면 confirm을 1회 재시도해야 한다")
    void should_retry_confirm_once_when_toss_lookup_has_no_payment() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, "payment-key-1");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(true);
        when(tossPaymentClient.getPayment("payment-key-1"))
            .thenThrow(tossException(TossPaymentFailureType.UNCERTAIN, "NOT_FOUND_PAYMENT", 404));
        when(tossPaymentClient.confirm(any(), eq("1"))).thenReturn(tossPayment("DONE"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(tossPaymentClient).confirm(any(), eq("1"));
        verify(orderClient).updateCompleteOrder(123L);
        verify(stockReservationService).complete(1L);
    }

    @Test
    @DisplayName("Toss 조회와 confirm 재시도가 모두 불확실하면 EXECUTING 상태를 유지해야 한다")
    void should_keep_executing_when_lookup_and_retry_confirm_are_uncertain() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, "payment-key-1");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(true);
        when(tossPaymentClient.getPayment("payment-key-1")).thenReturn(tossPayment("READY"));
        when(tossPaymentClient.confirm(any(), eq("1")))
            .thenThrow(tossException(TossPaymentFailureType.UNCERTAIN, "PROVIDER_ERROR", 400));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
        verify(tossPaymentClient, times(1)).confirm(any(), eq("1"));
        verify(paymentRepository, never()).save(payment);
        verify(orderClient, never()).updateCompleteOrder(123L);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(stockReservationService, never()).complete(1L);
        verify(stockReservationService, never()).restore(1L);
    }

    @Test
    @DisplayName("CAS 성공 후 예약 전에 종료된 결제는 Toss 조회와 재고 복구 없이 실패 처리해야 한다")
    void should_fail_payment_without_toss_lookup_and_stock_restore_when_no_reservation_exists() {
        // Given
        LocalDateTime applicationStartupTime = LocalDateTime.parse("2026-08-17T10:00:00");
        Payment payment = executingPayment(1L, 123L, "payment-key-1");
        when(paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        )).thenReturn(List.of(payment));
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(false);

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(orderClient).updatePendingOrder(123L);
        verify(tossPaymentClient, never()).getPayment("payment-key-1");
        verify(stockReservationService, never()).restore(1L);
        verify(stockReservationService, never()).complete(1L);
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
        when(stockReservationService.hasAnyReservation(1L)).thenReturn(true);
        when(stockReservationService.hasAnyReservation(2L)).thenReturn(true);
        when(tossPaymentClient.getPayment("payment-key-1")).thenThrow(new RuntimeException("toss lookup failed"));
        when(tossPaymentClient.getPayment("payment-key-2")).thenReturn(tossPayment("DONE"));

        // When
        paymentRecoveryService.recoverExecutingPaymentsBefore(applicationStartupTime);

        // Then
        assertThat(failedToRecover.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(recoverable.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderClient).updateCompleteOrder(456L);
        verify(stockReservationService).complete(2L);
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

    private PaymentTossPaymentProcessingException tossException(
        TossPaymentFailureType failureType,
        String tossErrorCode,
        Integer httpStatus
    ) {
        return new PaymentTossPaymentProcessingException(
            1L,
            "payment-key-1",
            tossErrorCode,
            httpStatus,
            failureType,
            "toss failed",
            new RuntimeException("toss failed")
        );
    }

}
