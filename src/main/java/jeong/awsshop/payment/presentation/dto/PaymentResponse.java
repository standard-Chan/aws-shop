package jeong.awsshop.payment.presentation.dto;

import java.math.BigDecimal;
import jeong.awsshop.payment.domain.PaymentStatus;


/**
 * 결제 응답 DTO
 * @param id 결제 ID
 * @param orderId 주문 ID
 * @param status 결제 상태
 * @param amount 결제 금액
 */
public record PaymentResponse (Long id, Long orderId, PaymentStatus status, BigDecimal amount) {
}
