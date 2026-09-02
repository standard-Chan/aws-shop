package jeong.awsshop.payment.infrastructure.tosspayment.dto;

public record TossPaymentErrorResponse(
    String code,
    String message
) {
}
