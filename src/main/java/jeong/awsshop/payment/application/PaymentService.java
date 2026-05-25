package jeong.awsshop.payment.application;

import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentNotFoundException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
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
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 주문 id에 해당하는 결제를 생성하여 반환한다.
     *
     * @param request
     * @return psp 결제 URL
     */
    // TODO : 현재는 사용자가 이를 호출하지만, 실제로는 주문이 생성될 때 주문 서비스에서 이를 호출하는 형태가 되어야한다.
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
            .id(snowflakeIdGenerator.nextId())
            .orderId(request.orderId())
            .amount(order.totalAmount())
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

        // 결제 승인 처리 시작. 상태 등록
        payment.start(confirmRequest.paymentKey());

        try {
            // 검증
            payment.validateOrderId(confirmRequest.orderId());
            payment.confirm(confirmRequest.amount());
            // toss 결제 승인 요청
            TossPaymentConfirmResponse response = tossPaymentClient.confirm(confirmRequest);

            // 결제 승인 완료
            payment.complete();

            log.info("[Payment] 결제 승인 완료. paymentKey={}, orderId={}, amount={}",
                response.paymentKey(), response.orderId(), response.totalAmount());

            // Order 완료 처리
            orderClient.updateCompleteOrder(payment.getOrderId());

            // TODO : 재고 감소 처리

            return response;
        } catch (PaymentException exception) {
            // 해당 결제 실패 처리
            payment.fail();

            // Order 상태 pending 변경
            orderClient.updatePendingOrder(payment.getOrderId());

            // TODO : 만약 재고가 감소된 상태라면, 재고 복구 처리 (재고 감소가 마지막이므로 로직상 문제가 없다면, 재고 감소가 실패한 경우는 없을 것이다.)

            log.warn("[Payment] 결제 실패. {} \n paymentKey={}, orderId={}, amount={}", exception,
                confirmRequest.paymentKey(), confirmRequest.orderId(), confirmRequest.amount());
            throw new PaymentConfirmExternalException(confirmRequest.paymentId(),
                confirmRequest.paymentKey(), exception);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new PaymentException("[Payment] 알 수 없는 에러가 발생하였습니다.", e);
        }
    }
}
