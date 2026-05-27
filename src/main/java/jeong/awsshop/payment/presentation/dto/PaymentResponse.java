package jeong.awsshop.payment.presentation.dto;

import java.math.BigDecimal;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentStatus;


/**
 * 결제 응답 DTO
 * @param paymentId 결제 ID
 * @param orderId 주문 ID
 * @param status 결제 상태
 * @param amount 결제 금액
 */
public record PaymentResponse (String paymentId, Long orderId, PaymentStatus status, BigDecimal amount) {

    /**
     * Payment 엔티티를 API 응답 DTO로 변환한다.
     *
     * 의도:
     * 응답 변환 책임을 service 밖으로 이동시켜 application service 가 흐름 제어에만 집중하게 한다.
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            String.valueOf(payment.getId()),
            payment.getOrderId(),
            payment.getStatus(),
            payment.getAmount()
        );
    }
}
