package jeong.awsshop.payment.application;

import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.domain.dto.OrderSummary;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentNotFoundException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.OrderClient;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final OrderClient orderClient;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

    /**
     * 주문 id에 해당하는 결제를 생성하여 반환한다.
     *
     * @param request
     * @return psp 결제 URL
     */
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("[Payment] 결제 생성 orderId={}", request.orderId());
        OrderSummary order;
        try {
            // 주문 정보 조회
            order = orderClient.getOrder(request.orderId());
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(request.orderId(), exception);
        }

        // 결제 Entity 생성
        Payment payment = Payment.builder()
            .orderId(request.orderId())
            .amount(order.getTotalPrice())
            .status(PaymentStatus.NOT_STARTED)
            .build();
        Payment savedPayment = paymentRepository.save(payment);

        // 응답 생성
        PaymentResponse response = new PaymentResponse(savedPayment.getId(),
            savedPayment.getOrderId(),
            savedPayment.getStatus(), savedPayment.getAmount());

        log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", request.orderId(), savedPayment.getId());

        return response;
    }

    /**
     * 결제 승인 요청을 처리한다.
     */
    @Transactional
    public TossPaymentConfirmResponse confirmPayment(TossPaymentConfirmRequest confirmRequest) {
        log.info("[Payment] 결제 승인 요청, 결제 정보 : 결제 id={}, 주문 id={}, 결제 금액={}",
            confirmRequest.paymentKey(),
            confirmRequest.orderId(), confirmRequest.amount());

        Payment payment = paymentRepository.findById(confirmRequest.paymentId())
            .orElseThrow(() -> new PaymentNotFoundException(confirmRequest.paymentId()));

        // 검증
        payment.start();
        payment.confirm(confirmRequest.amount());

        try {
            // toss 결제 승인 요청
            TossPaymentConfirmResponse response = tossPaymentClient.confirm(confirmRequest);

            // 결제 승인 완료
            payment.complete();

            // TODO : Order 완료 처리
            // TODO : 재고 감소 처리

            log.info("[Payment] 결제 승인 완료. paymentKey={}, orderId={}, amount={}",
                response.paymentKey(), response.orderId(), response.totalAmount());

            return response;
        } catch (RuntimeException exception) {
            // 해당 결제 실패 처리
            // TODO : 결제 상태 변경
            // TODO : Order 실패 처리
            // TODO : 재고 원복 처리

            log.warn("[Payment] 결제 실패. paymentKey={}, orderId={}, amount={}",
                confirmRequest.paymentKey(), confirmRequest.orderId(), confirmRequest.amount());
            throw new PaymentConfirmExternalException(confirmRequest.paymentId(), confirmRequest.paymentKey(),
                exception);
        }
    }
}
