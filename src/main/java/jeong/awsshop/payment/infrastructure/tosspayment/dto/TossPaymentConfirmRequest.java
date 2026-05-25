package jeong.awsshop.payment.infrastructure.tosspayment.dto;

import java.math.BigDecimal;

/**
 * 토스페이먼트 결제 승인 요청 DTO
 * @param orderId : toss의 order id는 현 결제 시스템의 결제 id이다. 즉, paymentId가 toss의 orderId로 사용된다.
 * @param paymentKey
 * @param amount
 */
public record TossPaymentConfirmRequest (
    Long orderId,
    String paymentKey,
    BigDecimal amount
) {
}
