package jeong.awsshop.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockTossPaymentClientTest {

    @Test
    @DisplayName("confirm 호출 횟수와 요청 이력을 기록하고 reset으로 초기화해야 한다")
    void should_record_confirm_count_and_requests_then_reset() {
        // Given
        MockTossPaymentClient client = new MockTossPaymentClient();
        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
            1L,
            "payment-key-1",
            new BigDecimal("140000.00")
        );

        // When
        TossPaymentConfirmResponse response = client.confirm(request);

        // Then
        assertThat(response.paymentKey()).isEqualTo("payment-key-1");
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(client.confirmCount()).isEqualTo(1);
        assertThat(client.confirmRequests()).containsExactly(request);

        // When
        client.reset();

        // Then
        assertThat(client.confirmCount()).isZero();
        assertThat(client.confirmRequests()).isEmpty();
    }
}
