package jeong.awsshop.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import jeong.awsshop.payment.exception.PaymentAmountMismatchException;
import jeong.awsshop.payment.exception.PaymentInvalidAmountException;
import jeong.awsshop.payment.exception.PaymentInvalidPaymentKey;
import jeong.awsshop.payment.exception.PaymentInvalidStatusException;
import jeong.awsshop.payment.exception.PaymentOrderIdMismatchException;
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

    // toss 결제 고유 키 (결제 승인 요청, 환불, 취소 시 필요)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    // 결제 총 금액
    @Column(precision = 13, scale = 4, nullable = false)
    private BigDecimal amount;

    /** 결제 시작 처리
     * 처리 : 상태 변경 및 결제 고유 키 등록
     * 호출 시점 : 결제 승인 요청 시, Toss 측에서 발급한 paymentKey를 Frontend에서 받아서 등록한다.
     * */
    public void start(String paymentKey) {
        if (this.status != PaymentStatus.NOT_STARTED) {
            throw new PaymentInvalidStatusException(PaymentStatus.NOT_STARTED, this.status);
        }
        this.status = PaymentStatus.EXECUTING;

        registerPaymentKey(paymentKey);
    }

    private void registerPaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new PaymentInvalidPaymentKey(paymentKey);
        }
        this.paymentKey = paymentKey;
    }

    /** 주문 id 동일 검증 */
    public void validateOrderId(Long requestedOrderId) {
        if (!this.orderId.equals(requestedOrderId)) {
            throw new PaymentOrderIdMismatchException(this.orderId, requestedOrderId);
        }
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
        if (this.status != PaymentStatus.EXECUTING) {
            throw new PaymentInvalidStatusException(PaymentStatus.EXECUTING, this.status);
        }
        this.status = PaymentStatus.SUCCESS;
    }

    /** 결제 실패 처리 */
    public void fail() {
        // 결제 실패는 결제 진행 중인 상태에서만 가능하다.
        // 만약, SUCCESS 상태에서 취소가 된 경우, 잘못된 로직이므로 반드시 수정이 필요하다.
        if (this.status != PaymentStatus.EXECUTING) {
            throw new PaymentInvalidStatusException(PaymentStatus.EXECUTING, this.status, "결제 실패 처리 중 상태 예외가 발생하였습니다.");
        }
        this.status = PaymentStatus.FAILED;
    }
}
