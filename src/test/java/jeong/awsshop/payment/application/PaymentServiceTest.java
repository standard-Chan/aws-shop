package jeong.awsshop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentExpiredException;
import jeong.awsshop.payment.exception.PaymentInvalidStatusException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCanceledException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCompletedException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyExecutingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderExpiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import jeong.awsshop.stock.application.StockService;
import jeong.awsshop.stock.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    @Mock
    private StockService stockService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
            orderClient,
            paymentRepository,
            tossPaymentClient,
            snowflakeIdGenerator,
            stockService
        );
    }

    @Test
    @DisplayName("주문 조회가 정상적이면 주문 금액과 초기 상태로 결제를 생성해야 한다")
    void should_create_payment_with_order_amount_and_not_started_status_when_order_exists() {
        // Given
        // 주문 조회 결과와 저장된 결제 엔티티를 준비한다.
        when(orderClient.updateExecutingStatus(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of());
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
    @DisplayName("주문이 이미 처리 중이면 기존 활성 결제를 실패 처리하고 새 결제를 생성해야 한다")
    void should_fail_existing_active_payment_and_create_new_payment_when_order_is_already_executing() {
        // Given
        when(orderClient.updateExecutingStatus(123L)).thenThrow(
            new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing"))
        );
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
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
        when(snowflakeIdGenerator.nextId()).thenReturn(56L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentService.createPayment(new CreatePaymentRequest(123L));

        // Then
        assertThat(activePayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.paymentId()).isEqualTo("56");
        assertThat(response.orderId()).isEqualTo(123L);
        assertThat(response.status()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        verify(paymentRepository).saveAll(List.of(activePayment));
    }

    @Test
    @DisplayName("기존 만료 결제는 변경하지 않고 새 결제를 생성해야 한다")
    void should_create_new_payment_without_changing_expired_payment() {
        // Given
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing")))
            ;
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
        Payment expiredPayment = Payment.builder()
            .id(55L)
            .orderId(123L)
            .status(PaymentStatus.EXPIRED)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(10))
            .expiresAt(LocalDateTime.now().minusMinutes(5))
            .build();
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of());
        when(snowflakeIdGenerator.nextId()).thenReturn(56L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentService.createPayment(new CreatePaymentRequest(123L));

        // Then
        assertThat(expiredPayment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(response.paymentId()).isEqualTo("56");
        assertThat(response.status()).isEqualTo(PaymentStatus.NOT_STARTED);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문은 executing 이지만 활성 결제가 없어도 새 결제를 생성해야 한다")
    void should_create_new_payment_when_active_payment_is_missing() {
        // Given
        when(orderClient.updateExecutingStatus(123L))
            .thenThrow(new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing")))
            ;
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of());
        when(snowflakeIdGenerator.nextId()).thenReturn(56L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentService.createPayment(new CreatePaymentRequest(123L));

        // Then
        assertThat(response.paymentId()).isEqualTo("56");
        assertThat(response.status()).isEqualTo(PaymentStatus.NOT_STARTED);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("활성 결제가 2개 이상이어도 모두 실패 처리하고 새 결제를 생성해야 한다")
    void should_fail_all_active_payments_and_create_new_payment_when_multiple_active_payments_exist() {
        // Given
        when(orderClient.updateExecutingStatus(123L)).thenThrow(
            new PaymentOrderAlreadyExecutingException(123L, new RuntimeException("already executing"))
        );
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
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
        when(snowflakeIdGenerator.nextId()).thenReturn(56L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentService.createPayment(new CreatePaymentRequest(123L));

        // Then
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.paymentId()).isEqualTo("56");
        assertThat(response.status()).isEqualTo(PaymentStatus.NOT_STARTED);
        verify(paymentRepository).saveAll(List.of(first, second));
    }

    @Test
    @DisplayName("저장소 예외가 발생하면 예외를 그대로 전파해야 한다")
    void should_propagate_exception_when_payment_repository_save_fails() {
        // Given
        // 주문 조회는 성공하지만 저장소가 예외를 던지도록 준비한다.
        when(orderClient.updateExecutingStatus(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));
        when(paymentRepository.findAllByOrderIdAndStatusIn(
            123L, List.of(PaymentStatus.NOT_STARTED, PaymentStatus.EXECUTING)))
            .thenReturn(List.of());
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

    @Test
    @DisplayName("결제 승인 전에 재고를 예약하고 승인 성공 시 결제와 주문을 완료해야 한다")
    void should_reserve_stock_before_toss_confirm_and_complete_payment() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        TossPaymentConfirmResponse tossResponse = tossConfirmResponse();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());
        when(tossPaymentClient.confirm(any())).thenReturn(tossResponse);
        when(paymentRepository.save(payment)).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
            assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");
            return null;
        }).when(stockService).decrease(10L, 2);

        // When
        TossPaymentConfirmResponse response = paymentService.confirmPayment(request);

        // Then
        assertThat(response).isEqualTo(tossResponse);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(stockService, never()).increase(10L, 2);
        verify(stockService, never()).increase(20L, 1);
        verify(orderClient).updateCompleteOrder(123L);
        verify(paymentRepository, times(2)).save(payment);

        InOrder inOrder = inOrder(stockService, paymentRepository, tossPaymentClient);
        inOrder.verify(stockService).decrease(10L, 2);
        inOrder.verify(stockService).decrease(20L, 1);
        inOrder.verify(paymentRepository).save(payment);
        inOrder.verify(tossPaymentClient).confirm(any());
    }

    @Test
    @DisplayName("결제 시작 후 재고를 예약하고 Toss 승인 요청 전에 EXECUTING 상태를 저장해야 한다")
    void should_save_executing_payment_before_toss_confirm_after_stock_reservation() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        TossPaymentConfirmResponse tossResponse = tossConfirmResponse();
        AtomicInteger saveCount = new AtomicInteger();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());
        when(tossPaymentClient.confirm(any())).thenReturn(tossResponse);
        doAnswer(invocation -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
            assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");
            return null;
        }).when(stockService).decrease(10L, 2);
        doAnswer(invocation -> {
            int currentSaveCount = saveCount.incrementAndGet();
            if (currentSaveCount == 1) {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
                assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");
            }
            return invocation.getArgument(0);
        }).when(paymentRepository).save(payment);

        // When
        paymentService.confirmPayment(request);

        // Then
        assertThat(saveCount).hasValue(2);
        InOrder inOrder = inOrder(stockService, paymentRepository, tossPaymentClient);
        inOrder.verify(stockService).decrease(10L, 2);
        inOrder.verify(stockService).decrease(20L, 1);
        inOrder.verify(paymentRepository).save(payment);
        inOrder.verify(tossPaymentClient).confirm(any());
    }

    @Test
    @DisplayName("만료된 결제 승인 요청은 결제를 만료 처리하고 주문을 pending으로 되돌린 뒤 외부 처리를 시작하지 않아야 한다")
    void should_expire_payment_and_restore_order_to_pending_without_external_processing_when_confirm_payment_is_expired() {
        // Given
        Payment payment = expiredPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentExpiredException.class)
            .hasMessage("[Payment] 결제가 만료되었습니다. orderId=123, paymentId=1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payment.getPaymentKey()).isNull();
        verify(paymentRepository).save(payment);
        verify(orderClient).getOrder(123L);
        verify(orderClient).updatePendingOrder(123L);
        verify(orderClient, never()).updateCompleteOrder(123L);
        verify(stockService, never()).decrease(any(), any(Integer.class));
        verify(stockService, never()).increase(any(), any(Integer.class));
        verify(tossPaymentClient, never()).confirm(any());
    }

    @Test
    @DisplayName("이미 종료된 결제이면 승인 실패 처리 없이 승인 흐름을 시작하지 않아야 한다")
    void should_reject_finished_payment_without_marking_failed_when_payment_is_already_finished() {
        // Given
        Payment payment = paymentWithStatus(1L, 123L, new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentInvalidStatusException.class)
            .hasMessage("[Payment] 결제 승인 요청을 시작할 수 없는 상태입니다. expected=NOT_STARTED, actual=SUCCESS");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository, never()).save(payment);
        verify(orderClient, never()).updatePendingOrder(123L);
        verify(stockService, never()).decrease(any(), any(Integer.class));
        verify(tossPaymentClient, never()).confirm(any());
    }

    @Test
    @DisplayName("이미 완료된 주문이면 결제 승인 흐름을 시작하지 않아야 한다")
    void should_reject_confirm_payment_without_starting_payment_when_order_is_completed() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L))
            .thenReturn(createOrderSummary(123L, new BigDecimal("100.00"), OrderStatus.COMPLETED));

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentOrderAlreadyCompletedException.class)
            .hasMessage("[Payment-Order] 이미 완료된 주문입니다. orderId=123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(payment.getPaymentKey()).isNull();
        verify(paymentRepository, never()).save(payment);
        verify(stockService, never()).decrease(any(), any(Integer.class));
        verify(tossPaymentClient, never()).confirm(any());
        verify(orderClient, never()).updateCompleteOrder(123L);
        verify(orderClient, never()).updatePendingOrder(123L);
    }

    @Test
    @DisplayName("이미 취소된 주문이면 결제 승인 흐름을 시작하지 않아야 한다")
    void should_reject_confirm_payment_without_starting_payment_when_order_is_canceled() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L))
            .thenReturn(createOrderSummary(123L, new BigDecimal("100.00"), OrderStatus.CANCELED));

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentOrderAlreadyCanceledException.class)
            .hasMessage("[Payment-Order] 이미 실패한 주문입니다. orderId=123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(payment.getPaymentKey()).isNull();
        verify(paymentRepository, never()).save(payment);
        verify(stockService, never()).decrease(any(), any(Integer.class));
        verify(tossPaymentClient, never()).confirm(any());
        verify(orderClient, never()).updateCompleteOrder(123L);
        verify(orderClient, never()).updatePendingOrder(123L);
    }

    @Test
    @DisplayName("만료된 주문이면 결제 승인 흐름을 시작하지 않아야 한다")
    void should_reject_confirm_payment_without_starting_payment_when_order_is_expired() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L))
            .thenReturn(createOrderSummary(123L, new BigDecimal("100.00"), OrderStatus.EXPIRED));

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentOrderExpiredException.class)
            .hasMessage("[Payment-Order] 만료된 주문입니다. orderId=123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
        assertThat(payment.getPaymentKey()).isNull();
        verify(paymentRepository, never()).save(payment);
        verify(stockService, never()).decrease(any(), any(Integer.class));
        verify(tossPaymentClient, never()).confirm(any());
        verify(orderClient, never()).updateCompleteOrder(123L);
        verify(orderClient, never()).updatePendingOrder(123L);
    }

    @Test
    @DisplayName("재고 예약이 실패하면 Toss 승인 요청 없이 결제를 실패 처리해야 한다")
    void should_fail_payment_without_toss_confirm_when_stock_reservation_fails() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());
        when(stockService.decrease(10L, 2)).thenThrow(new InsufficientStockException(10L, 2, 0));

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentConfirmExternalException.class)
            .hasRootCauseInstanceOf(InsufficientStockException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(tossPaymentClient, never()).confirm(any());
        verify(stockService, never()).increase(10L, 2);
        verify(stockService, never()).increase(20L, 1);
        verify(orderClient).updatePendingOrder(123L);
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("일부 주문 라인 재고 예약 후 다음 라인이 실패하면 이미 예약한 재고를 복구해야 한다")
    void should_restore_already_reserved_stock_when_later_order_line_reservation_fails() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());
        when(stockService.decrease(10L, 2)).thenReturn(null);
        when(stockService.decrease(20L, 1)).thenThrow(new InsufficientStockException(20L, 1, 0));

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentConfirmExternalException.class)
            .hasRootCauseInstanceOf(InsufficientStockException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(tossPaymentClient, never()).confirm(any());
        verify(stockService).increase(10L, 2);
        verify(stockService, never()).increase(20L, 1);
        verify(orderClient).updatePendingOrder(123L);
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("재고 예약 후 결제 승인이 실패하면 예약 재고를 복구해야 한다")
    void should_restore_reserved_stock_when_toss_confirm_fails_after_stock_reservation() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        PaymentException tossException = new PaymentException("psp confirm failed");
        AtomicInteger saveCount = new AtomicInteger();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummaryWithItems());
        when(tossPaymentClient.confirm(any())).thenThrow(tossException);
        doAnswer(invocation -> {
            int currentSaveCount = saveCount.incrementAndGet();
            if (currentSaveCount == 1) {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
                assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");
            }
            if (currentSaveCount == 2) {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            }
            return invocation.getArgument(0);
        }).when(paymentRepository).save(payment);

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentConfirmExternalException.class)
            .hasRootCauseMessage("psp confirm failed");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saveCount).hasValue(2);
        verify(orderClient).updatePendingOrder(123L);
        verify(stockService).increase(10L, 2);
        verify(stockService).increase(20L, 1);
        verify(paymentRepository, times(2)).save(payment);
    }

    @Test
    @DisplayName("주문 상품 라인이 비어 있으면 재고 차감 없이 결제를 실패 처리해야 한다")
    void should_fail_payment_without_stock_decrease_when_order_items_are_empty() {
        // Given
        Payment payment = notStartedPayment(1L, 123L, new BigDecimal("100.00"));
        ConfirmPaymentRequest request = confirmRequest();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.getOrder(123L)).thenReturn(createOrderSummary(123L, new BigDecimal("100.00")));

        // When, Then
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
            .isInstanceOf(PaymentConfirmExternalException.class)
            .hasRootCauseMessage("[Payment] 주문 상품 정보가 없습니다. orderId=123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(stockService, never()).decrease(any(), any(Integer.class));
        verify(tossPaymentClient, never()).confirm(any());
        verify(orderClient).updatePendingOrder(123L);
        verify(paymentRepository).save(payment);
    }

    private OrderSummary createOrderSummary(Long orderId, BigDecimal totalPrice) {
        return createOrderSummary(orderId, totalPrice, OrderStatus.EXECUTING);
    }

    private OrderSummary createOrderSummary(Long orderId, BigDecimal totalPrice, OrderStatus status) {
        return new OrderSummary(
            orderId,
            1L,
            status,
            totalPrice,
            "Seoul",
            List.of()
        );
    }

    private OrderSummary createOrderSummaryWithItems() {
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

    private Payment notStartedPayment(Long paymentId, Long orderId, BigDecimal amount) {
        return paymentWithStatus(paymentId, orderId, amount, PaymentStatus.NOT_STARTED);
    }

    private Payment paymentWithStatus(Long paymentId, Long orderId, BigDecimal amount, PaymentStatus status) {
        return Payment.builder()
            .id(paymentId)
            .orderId(orderId)
            .status(status)
            .amount(amount)
            .createdAt(LocalDateTime.now().minusMinutes(1))
            .expiresAt(LocalDateTime.now().plusMinutes(4))
            .build();
    }

    private Payment expiredPayment(Long paymentId, Long orderId, BigDecimal amount) {
        return Payment.builder()
            .id(paymentId)
            .orderId(orderId)
            .status(PaymentStatus.NOT_STARTED)
            .amount(amount)
            .createdAt(LocalDateTime.now().minusMinutes(10))
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
    }

    private ConfirmPaymentRequest confirmRequest() {
        return new ConfirmPaymentRequest(
            "payment-key-1",
            1L,
            123L,
            new BigDecimal("140000.00")
        );
    }

    private TossPaymentConfirmResponse tossConfirmResponse() {
        return new TossPaymentConfirmResponse(
            "payment-key-1",
            "123",
            "CARD",
            "DONE",
            140000L,
            OffsetDateTime.parse("2026-05-25T10:15:30+09:00"),
            OffsetDateTime.parse("2026-05-25T10:16:00+09:00")
        );
    }
}
