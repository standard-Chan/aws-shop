package jeong.awsshop.payment.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import jeong.awsshop.payment.application.PaymentService;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentExpiredException;
import jeong.awsshop.payment.exception.PaymentRecoveryRequiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCanceledException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCompletedException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderExpiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("결제 생성 요청이 정상적이면 service 결과를 HTTP 200 JSON으로 반환해야 한다")
    void should_return_payment_response_when_create_payment_request_is_valid() throws Exception {
        // Given
        // service가 생성된 결제 응답을 반환하도록 준비한다.
        PaymentResponse response = new PaymentResponse(
            "1",
            123L,
            PaymentStatus.NOT_STARTED,
            new java.math.BigDecimal("100.00")
        );
        when(paymentService.createPayment(new CreatePaymentRequest(123L))).thenReturn(response);

        // When
        // 결제 생성 API를 JSON 본문으로 호출한다.
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 123
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value("1"))
            .andExpect(jsonPath("$.orderId").value(123L))
            .andExpect(jsonPath("$.status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.amount").value(100.00));

        // Then
        // controller는 orderId를 service에 전달하고 응답 필드를 그대로 반환해야 한다.
    }

    @Test
    @DisplayName("결제 생성 중 service 예외가 발생하면 예외를 그대로 전파해야 한다")
    void should_propagate_exception_when_create_payment_service_throws_exception() {
        // Given
        // service가 결제 생성 중 예외를 던지도록 준비한다.
        when(paymentService.createPayment(new CreatePaymentRequest(123L)))
            .thenThrow(new PaymentOrderLookupException(123L, new RuntimeException("order lookup failed")));

        // When
        // 결제 생성 API를 호출한다.
        // Then
        // controller는 예외를 성공 응답으로 바꾸지 않고 그대로 전파해야 한다.
        assertThatThrownBy(() -> mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 123
                    }
                    """)))
            .hasCauseInstanceOf(PaymentOrderLookupException.class)
            .hasRootCauseMessage("order lookup failed");
    }

    @Test
    @DisplayName("결제 생성 중 만료된 결제가 감지되면 HTTP 410으로 반환해야 한다")
    void should_return_gone_when_create_payment_detects_expired_payment() throws Exception {
        // Given
        when(paymentService.createPayment(new CreatePaymentRequest(123L)))
            .thenThrow(new PaymentExpiredException(123L, 55L));

        // When, Then
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                      "orderId": 123
                    }
                    """))
            .andExpect(status().isGone())
            .andExpect(content().string("[Payment] 결제가 만료되었습니다. orderId=123, paymentId=55"));
    }

    @Test
    @DisplayName("결제 생성 중 장애 복구가 수행되면 HTTP 409로 재시도 응답을 반환해야 한다")
    void should_return_conflict_when_create_payment_requires_recovery() throws Exception {
        // Given
        when(paymentService.createPayment(new CreatePaymentRequest(123L)))
            .thenThrow(new PaymentRecoveryRequiredException(123L));

        // When, Then
        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                      "orderId": 123
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().string("[Payment] 결제 처리 상태를 복구했습니다. 다시 시도해주세요. orderId=123"));
    }

    @Test
    @DisplayName("완료된 주문으로 결제 생성 요청이 오면 HTTP 409를 반환해야 한다")
    void should_return_conflict_when_create_payment_is_requested_for_completed_order() throws Exception {
        when(paymentService.createPayment(new CreatePaymentRequest(123L)))
            .thenThrow(new PaymentOrderAlreadyCompletedException(123L, new RuntimeException("completed")));

        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 123
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().string("[Payment-Order] 이미 완료된 주문입니다. orderId=123"));
    }

    @Test
    @DisplayName("취소된 주문으로 결제 생성 요청이 오면 HTTP 409를 반환해야 한다")
    void should_return_conflict_when_create_payment_is_requested_for_canceled_order() throws Exception {
        when(paymentService.createPayment(new CreatePaymentRequest(123L)))
            .thenThrow(new PaymentOrderAlreadyCanceledException(123L, new RuntimeException("canceled")));

        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 123
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().string("[Payment-Order] 이미 실패한 주문입니다. orderId=123"));
    }

    @Test
    @DisplayName("만료된 주문으로 결제 생성 요청이 오면 HTTP 410을 반환해야 한다")
    void should_return_gone_when_create_payment_is_requested_for_expired_order() throws Exception {
        when(paymentService.createPayment(new CreatePaymentRequest(123L)))
            .thenThrow(new PaymentOrderExpiredException(123L, new RuntimeException("expired")));

        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 123
                    }
                    """))
            .andExpect(status().isGone())
            .andExpect(content().string("[Payment-Order] 만료된 주문입니다. orderId=123"));
    }

    @Test
    @DisplayName("결제 승인 요청이 정상적이면 Toss 응답을 HTTP 200 JSON으로 반환해야 한다")
    void should_return_toss_confirm_response_when_confirm_payment_request_is_valid() throws Exception {
        // Given
        // Toss client가 승인 완료 응답을 반환하도록 준비한다.
        TossPaymentConfirmResponse response = new TossPaymentConfirmResponse(
            "payment-key-1",
            "123",
            "CARD",
            "DONE",
            100L,
            OffsetDateTime.parse("2026-05-25T10:15:30+09:00"),
            OffsetDateTime.parse("2026-05-25T10:16:00+09:00")
        );
        when(paymentService.confirmPayment(any(ConfirmPaymentRequest.class))).thenReturn(response);

        // When
        // 결제 승인 API를 JSON 본문으로 호출한다.
        mockMvc.perform(post("/api/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "paymentId": 1,
                      "paymentKey": "payment-key-1",
                      "orderId": 123,
                      "amount": 100
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentKey").value("payment-key-1"))
            .andExpect(jsonPath("$.orderId").value("123"))
            .andExpect(jsonPath("$.method").value("CARD"))
            .andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.totalAmount").value(100L));
    }

    @Test
    @DisplayName("결제 승인 중 Toss client 예외가 발생하면 예외를 그대로 전파해야 한다")
    void should_propagate_exception_when_confirm_payment_client_throws_exception() {
        // Given
        // service가 외부 승인 실패 예외를 던지도록 준비한다.
        when(paymentService.confirmPayment(any(ConfirmPaymentRequest.class)))
            .thenThrow(new PaymentConfirmExternalException(1L, "payment-key-1",
                new RuntimeException("psp confirm failed")));

        // When
        // 결제 승인 API를 호출한다.
        // Then
        // controller는 외부 승인 실패를 성공 응답으로 바꾸지 않고 그대로 전파해야 한다.
        assertThatThrownBy(() -> mockMvc.perform(post("/api/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "paymentId": 1,
                      "paymentKey": "payment-key-1",
                      "orderId": 123,
                      "amount": 100
                    }
                    """)))
            .hasCauseInstanceOf(PaymentConfirmExternalException.class)
            .hasRootCauseMessage("psp confirm failed");
    }
}
