package jeong.awsshop.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.exception.TossPaymentFailureType;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class TossPaymentClientTest {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    @Test
    @DisplayName("Toss confirm 요청에 paymentId 기반 멱등키를 전달해야 한다")
    void should_send_payment_id_as_idempotency_key_when_confirming_payment() {
        // Given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = tossPaymentClient(builder);
        TossPaymentConfirmRequest request = tossConfirmRequest();
        server.expect(requestTo(CONFIRM_URL))
            .andExpect(header("Idempotency-Key", "1"))
            .andExpect(header("Authorization", authorizationHeader()))
            .andRespond(withSuccess("""
                {
                  "paymentKey": "payment-key-1",
                  "orderId": "1",
                  "method": "CARD",
                  "status": "DONE",
                  "totalAmount": 140000,
                  "requestedAt": "2026-05-25T10:15:30+09:00",
                  "approvedAt": "2026-05-25T10:16:00+09:00"
                }
                """, MediaType.APPLICATION_JSON));

        // When
        TossPaymentConfirmResponse response = client.confirm(request, "1");

        // Then
        assertThat(response.status()).isEqualTo("DONE");
        server.verify();
    }

    @Test
    @DisplayName("Toss PROVIDER_ERROR는 승인 결과 불확실 오류로 분류해야 한다")
    void should_classify_provider_error_as_uncertain() {
        // Given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = tossPaymentClient(builder);
        server.expect(requestTo(CONFIRM_URL))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "code": "PROVIDER_ERROR",
                      "message": "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                    }
                    """));

        // When
        PaymentTossPaymentProcessingException exception = catchThrowableOfType(
            () -> client.confirm(tossConfirmRequest(), "1"),
            PaymentTossPaymentProcessingException.class
        );

        // Then
        assertThat(exception.getFailureType()).isEqualTo(TossPaymentFailureType.UNCERTAIN);
        assertThat(exception.getTossErrorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(exception.getHttpStatus()).isEqualTo(400);
        server.verify();
    }

    @Test
    @DisplayName("Toss INVALID_REQUEST는 확정 실패 오류로 분류해야 한다")
    void should_classify_invalid_request_as_confirmed_failure() {
        // Given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = tossPaymentClient(builder);
        server.expect(requestTo(CONFIRM_URL))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "code": "INVALID_REQUEST",
                      "message": "잘못된 요청입니다."
                    }
                    """));

        // When
        PaymentTossPaymentProcessingException exception = catchThrowableOfType(
            () -> client.confirm(tossConfirmRequest(), "1"),
            PaymentTossPaymentProcessingException.class
        );

        // Then
        assertThat(exception.getFailureType()).isEqualTo(TossPaymentFailureType.CONFIRMED_FAILURE);
        assertThat(exception.getTossErrorCode()).isEqualTo("INVALID_REQUEST");
        assertThat(exception.getHttpStatus()).isEqualTo(400);
        server.verify();
    }

    private TossPaymentClient tossPaymentClient(RestClient.Builder builder) {
        ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new TossPaymentClient("test_secret_key", builder.build(), objectMapper);
    }

    private TossPaymentConfirmRequest tossConfirmRequest() {
        return new TossPaymentConfirmRequest(
            1L,
            "payment-key-1",
            new BigDecimal("140000.00")
        );
    }

    private String authorizationHeader() {
        String encodedSecretKey = Base64.getEncoder()
            .encodeToString("test_secret_key:".getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedSecretKey;
    }
}
