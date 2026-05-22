package jeong.awsshop.payment.infrastructure.dto;

import java.time.OffsetDateTime;

public record TossPaymentConfirmResponse(

    String paymentKey,

    String orderId,

    // 결제 방법
    String method,

    // 결제 상태 : DONE, CANCELED, ABORTED 등
    String status,

    Long totalAmount,

    OffsetDateTime requestedAt,

    OffsetDateTime approvedAt

) {
}
