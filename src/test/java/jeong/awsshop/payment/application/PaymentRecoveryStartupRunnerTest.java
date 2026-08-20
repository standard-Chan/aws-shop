package jeong.awsshop.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryStartupRunnerTest {

    @Mock
    private PaymentRecoveryService paymentRecoveryService;

    @Test
    @DisplayName("서버 시작 복구는 현재 시각을 기준으로 EXECUTING 결제 복구를 요청해야 한다")
    void should_request_recovery_with_current_time_when_application_is_ready() {
        // Given
        PaymentRecoveryStartupRunner runner = new PaymentRecoveryStartupRunner(paymentRecoveryService);
        LocalDateTime before = LocalDateTime.now();

        // When
        runner.recoverExecutingPayments();

        // Then
        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentRecoveryService).recoverExecutingPaymentsBefore(timeCaptor.capture());
        assertThat(timeCaptor.getValue()).isBetween(before, after);
    }
}
