package jeong.awsshop.payment.application;

import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentExpiredException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentNotFoundException;
import jeong.awsshop.payment.exception.PaymentRecoveryRequiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyExecutingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final long PAYMENT_EXPIRATION_MINUTES = 5L;
    private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES = List.of(
        PaymentStatus.NOT_STARTED,
        PaymentStatus.EXECUTING
    );

    private final OrderClient orderClient;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 주문 id에 해당하는 결제를 생성하여 반환한다.
     * 목표 플로우:
     * 1) Order 상태를 통해 결제 생성 진입을 점유한다.
     * 2) 이미 처리 중인 상태라면, 기존 Payment를 조회해 만료 여부를 판단한다.
     * 3) 만료되지 않았다면 기존 Payment를 반환한다.
     * 4) 만료되었으면 만료 응답을 반환한다.
     * 5) 처리 중 Payment가 없으면 장애 복구 후 에러를 반환한다.
     *
     * 의도:
     * Payment 생성은 updateExecutingStatus 성공 경로에서만 수행한다.
     * order 가 EXECUTING 이라는 사실만으로 중복 결제라고 단정하지 않고, payment 데이터와 함께 재사용/만료/복구 필요 여부를 판단한다.
     *
     * @param request
     * @return psp 결제 URL
     *
     * // @Transactional : order server에 요청을 보내므로, 트랜잭션을 처리하지 않았습니다. DB 커넥션을 잡지 않기 위함입니다.
     */
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("[Payment] 결제 생성 orderId={}", request.orderId());

        OrderSummary order;
        try {
            order = orderClient.updateExecutingStatus(request.orderId());
        } catch (PaymentOrderAlreadyExecutingException exception) {
            return resolveAlreadyExecutingPayment(request.orderId());
        } catch (PaymentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(request.orderId(), exception);
        }

        return createNewPayment(order);
    }

    private PaymentResponse createNewPayment(OrderSummary order) {
        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = Payment.builder()
            .id(snowflakeIdGenerator.nextId())
            .orderId(order.orderId())
            .status(PaymentStatus.NOT_STARTED)
            .amount(order.totalAmount())
            .createdAt(createdAt)
            .expiresAt(createdAt.plusMinutes(PAYMENT_EXPIRATION_MINUTES))
            .build();

        try {
            return PaymentResponse.from(paymentRepository.save(payment));
        } catch (DataIntegrityViolationException exception) {
            return resolveAlreadyExecutingPayment(order.orderId());
        }
    }

    private PaymentResponse resolveAlreadyExecutingPayment(Long orderId) {
        List<Payment> activePayments = paymentRepository.findAllByOrderIdAndStatusIn(orderId, ACTIVE_PAYMENT_STATUSES);

        if (activePayments.size() > 1) {
            throw new PaymentException("[Payment] 처리 중인 결제가 2개 이상 존재합니다. orderId=" + orderId);
        }

        if (activePayments.isEmpty()) {
            orderClient.updatePendingOrder(orderId);
            throw new PaymentRecoveryRequiredException(orderId);
        }

        Payment payment = activePayments.get(0);
        if (payment.isExpired(LocalDateTime.now())) {
            payment.expire();
            paymentRepository.save(payment);
            throw new PaymentExpiredException(orderId, payment.getId());
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 결제 승인 요청을 처리한다. - @Transactional : 결제 승인 처리 과정에서 예외가 발생할 경우, 값의 변경이 초기화 되면 안되므로 적용하지 않았습니다. -
     * 다음 값이 초기화되면 안되는 값에 해당합니다. - paymentKey 등록 - status로 변경 (결제 진행 시 : EXECUTING , 실패 시 : FAILD)
     */
    public TossPaymentConfirmResponse confirmPayment(ConfirmPaymentRequest confirmRequest) {
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

            // TODO : 주문 상태 검증
            // 주문 상태가 COMPLETED인 경우, 결제 승인 요청이 들어오면, 주문이 이미 완료된 상태이므로, 결제 승인 요청을 실패 처리한다.
            // (실제 서비스에서는 주문 상태가 COMPLETED인 경우, 결제 승인 요청이 들어오지 않도록 프론트엔드에서 막는 것이 좋다.)

            // toss 결제 승인 요청
            TossPaymentConfirmResponse response = tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(confirmRequest.paymentId(),
                    confirmRequest.paymentKey(), confirmRequest.amount()));

            // 결제 승인 완료
            payment.complete();

            log.info("[Payment] 결제 승인 완료. paymentKey={}, paymentId={}, amount={}",
                response.paymentKey(), response.orderId(), response.totalAmount());

            // Order 완료 처리
            orderClient.updateCompleteOrder(payment.getOrderId());

            // TODO : 재고 감소 처리

            paymentRepository.save(payment);
            return response;
        } catch (PaymentException exception) {
            // 해당 결제 실패 처리
            payment.fail();
            paymentRepository.save(payment);

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
