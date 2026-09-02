package jeong.awsshop.payment.infrastructure.tosspayment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TossPaymentStatusTest {

    @Test
    @DisplayName("DONE 상태만 성공 상태로 판단해야 한다")
    void should_treat_done_as_success_status() {
        assertThat(TossPaymentStatus.isDone("DONE")).isTrue();
        assertThat(TossPaymentStatus.isDone("READY")).isFalse();
        assertThat(TossPaymentStatus.isDone(null)).isFalse();
    }

    @Test
    @DisplayName("취소, 중단, 만료 상태만 실패 상태로 판단해야 한다")
    void should_treat_canceled_aborted_expired_as_failed_status() {
        assertThat(TossPaymentStatus.isFailed("CANCELED")).isTrue();
        assertThat(TossPaymentStatus.isFailed("ABORTED")).isTrue();
        assertThat(TossPaymentStatus.isFailed("EXPIRED")).isTrue();
        assertThat(TossPaymentStatus.isFailed("READY")).isFalse();
        assertThat(TossPaymentStatus.isFailed(null)).isFalse();
    }
}
