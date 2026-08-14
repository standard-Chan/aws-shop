package jeong.awsshop.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jeong.awsshop.payment.exception.PaymentInvalidStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    @DisplayName("결제 시작 전 상태도 실패 처리할 수 있어야 한다")
    void should_fail_not_started_payment() {
        Payment payment = payment(PaymentStatus.NOT_STARTED);

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("결제 진행 중 상태는 실패 처리할 수 있어야 한다")
    void should_fail_executing_payment() {
        Payment payment = payment(PaymentStatus.EXECUTING);

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("성공한 결제는 실패 처리할 수 없어야 한다")
    void should_reject_fail_when_payment_is_success() {
        Payment payment = payment(PaymentStatus.SUCCESS);

        assertThatThrownBy(payment::fail)
            .isInstanceOf(PaymentInvalidStatusException.class);
    }

    @Test
    @DisplayName("이미 실패한 결제는 다시 실패 처리할 수 없어야 한다")
    void should_reject_fail_when_payment_is_already_failed() {
        Payment payment = payment(PaymentStatus.FAILED);

        assertThatThrownBy(payment::fail)
            .isInstanceOf(PaymentInvalidStatusException.class);
    }

    @Test
    @DisplayName("만료된 결제는 실패 처리할 수 없어야 한다")
    void should_reject_fail_when_payment_is_expired() {
        Payment payment = payment(PaymentStatus.EXPIRED);

        assertThatThrownBy(payment::fail)
            .isInstanceOf(PaymentInvalidStatusException.class);
    }

    @Test
    @DisplayName("활성 결제는 만료 처리할 수 있어야 한다")
    void should_expire_active_payment() {
        Payment payment = payment(PaymentStatus.NOT_STARTED);

        payment.expire();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 종료된 결제는 만료 처리할 수 없어야 한다")
    void should_reject_expire_when_payment_is_already_finished() {
        Payment payment = payment(PaymentStatus.SUCCESS);

        assertThatThrownBy(payment::expire)
            .isInstanceOf(PaymentInvalidStatusException.class);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
            .id(1L)
            .orderId(123L)
            .status(status)
            .amount(new BigDecimal("100.00"))
            .createdAt(LocalDateTime.now().minusMinutes(1))
            .expiresAt(LocalDateTime.now().plusMinutes(4))
            .build();
    }
}
