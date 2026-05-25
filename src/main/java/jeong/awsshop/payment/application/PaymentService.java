package jeong.awsshop.payment.application;

import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.domain.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.OrderClient;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderClient orderClient;
    private final String PSP_URL_TEMP = "http://localhost:5173/main";
    private final PaymentRepository paymentRepository;

    /**
     * 주문 id에 해당하는 결제를 생성하여 반환한다.
     * @param orderId
     * @return psp 결제 URL
     */
    public PaymentResponse createPayment(Long orderId) {
        // 주문 정보 조회
        OrderSummary order = orderClient.getOrder(orderId);

        // 결제 Entity 생성
        Payment payment = Payment.builder()
            .orderId(orderId)
            .amount(order.getTotalPrice())
            .status(PaymentStatus.NOT_STARTED)
            .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 응답 생성
        PaymentResponse response = new PaymentResponse(savedPayment.getId(), savedPayment.getOrderId(),
            savedPayment.getStatus(), savedPayment.getAmount());

        log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", orderId, savedPayment.getId());

        return response;
    }
}
