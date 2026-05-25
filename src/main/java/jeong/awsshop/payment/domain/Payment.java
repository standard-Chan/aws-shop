package jeong.awsshop.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import jeong.awsshop.payment.exception.PaymentAmountMismatchException;
import jeong.awsshop.payment.exception.PaymentInvalidAmountException;
import jeong.awsshop.payment.exception.PaymentInvalidStatusException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Payment {

    @Id
    private Long id;

    // 결제할 주문 정보
    private Long orderId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    // 결제 총 금액
    @Column(precision = 13, scale = 4, nullable = false)
    private BigDecimal amount;

    /** 결제 시작 처리 */
    public void start() {
        if (this.status != PaymentStatus.NOT_STARTED) {
            throw new PaymentInvalidStatusException(PaymentStatus.NOT_STARTED, this.status);
        }
        this.status = PaymentStatus.EXECUTING;
    }

    /** 결제 승인 요청 검증 */
    public void confirm(BigDecimal requestedAmount) {
        if (requestedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new PaymentInvalidAmountException(requestedAmount);
        }

        if (this.amount.compareTo(requestedAmount) != 0) {
            throw new PaymentAmountMismatchException(this.amount, requestedAmount);
        }

        if (this.status != PaymentStatus.EXECUTING) {
            throw new PaymentInvalidStatusException(PaymentStatus.EXECUTING, this.status);
        }
    }

    /** 결제 완료 처리 */
    public void complete() {
        this.status = PaymentStatus.SUCCESS;
    }
}
