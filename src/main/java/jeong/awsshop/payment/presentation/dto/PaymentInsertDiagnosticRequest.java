package jeong.awsshop.payment.presentation.dto;

import java.math.BigDecimal;

public record PaymentInsertDiagnosticRequest(
    Long orderId,
    BigDecimal amount,
    Long holdBeforeCommitMillis
) {
}
