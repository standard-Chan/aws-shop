package jeong.awsshop.payment.presentation.dto;

import java.math.BigDecimal;
import jeong.awsshop.payment.domain.PaymentStatus;

public record PaymentInsertDiagnosticResponse(
    Long paymentId,
    Long orderId,
    PaymentStatus status,
    BigDecimal amount,
    Long holdBeforeCommitMillis
) {
}
