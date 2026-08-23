package jeong.awsshop.payment.infrastructure;

import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;

public interface TossPaymentGateway {

    TossPaymentConfirmResponse confirm(TossPaymentConfirmRequest request);

    TossPaymentConfirmResponse getPayment(String paymentKey);
}
