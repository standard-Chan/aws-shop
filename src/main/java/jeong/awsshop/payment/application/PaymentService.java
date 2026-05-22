package jeong.awsshop.payment.application;

import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.domain.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.OrderClient;
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
     * 주문 id에 해당하는 psp 결제 링크를 반환한다.
     * @param orderId
     * @return psp 결제 URL
     */
    public String getPspRedirectionUrl(Long orderId) {
        // 주문 정보 조회
        OrderSummary order = orderClient.getOrder(orderId);

        // PSP URL 생성
        String redirectUrl = PSP_URL_TEMP + order.getTotalPrice();

        // 결제 Entity 생성
        Payment payment = Payment.builder()
            .orderId(orderId)
            .amount(order.getTotalPrice())
            .status(PaymentStatus.NOT_STARTED)
            .build();

        Payment savedPayment = paymentRepository.save(payment);

        log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", orderId, savedPayment.getId());

        return redirectUrl;
    }
}
