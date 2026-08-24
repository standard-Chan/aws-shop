package jeong.awsshop.payment.infrastructure;

import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;

public interface TossPaymentGateway {

    /** Toss 결제 승인을 요청한다. */
    TossPaymentConfirmResponse confirm(TossPaymentConfirmRequest request);

    /** Toss 결제 상태를 조회한다. */
    TossPaymentConfirmResponse getPayment(String paymentKey);
}
