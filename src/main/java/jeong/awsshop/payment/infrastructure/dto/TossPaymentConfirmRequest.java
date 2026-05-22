package jeong.awsshop.payment.infrastructure.dto;

public record TossPaymentConfirmRequest (
    String paymentKey,
    String orderId,
    Long amount
) {

}
