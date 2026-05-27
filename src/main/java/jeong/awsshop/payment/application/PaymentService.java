package jeong.awsshop.payment.application;

import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.DuplicatePaymentException;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentNotFoundException;
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
     * 4) 만료되었거나 처리 중 Payment가 없으면 장애 복구 후 새 Payment를 생성한다.
     *
     * 의도:
     * order 상태를 진입 제어 신호로 사용하되, 실제 재사용/재생성 판단은 payment 저장소의 활성 결제를 기준으로 수행한다.
     * 즉, order 가 EXECUTING 이라는 사실만으로 중복 결제라고 단정하지 않고, payment 데이터와 함께 복구 가능 여부를 판단한다.
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
            return recoverOrReuseExistingPayment(request.orderId());
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(request.orderId(), exception);
        }

        log.info("[Payment] 주문 상태 EXECUTING 갱신 완료 = {}", order.orderId());
        return createNewPayment(order, "INITIAL_CREATE");
    }

    /**
     * order 가 이미 EXECUTING 인 경우, 기존 활성 결제를 재사용할지 새로 생성할지 결정한다.
     *
     * 용어:
     * active payment 는 결제 진행 중이거나 결제 시작 전인 상태의 결제를 의미한다. (NOT_STARTED, EXECUTING)
     */
    private PaymentResponse recoverOrReuseExistingPayment(Long orderId) {
        List<Payment> activePayments = paymentRepository.findAllByOrderIdAndStatusIn(orderId, ACTIVE_PAYMENT_STATUSES);

        if (activePayments.size() > 1) {
            log.error("[Payment] 처리 중인 결제가 2개 이상 존재합니다. orderId={}, count={}",
                orderId, activePayments.size());
            throw new PaymentException("[Payment] 처리 중인 결제가 2개 이상 존재합니다. orderId=" + orderId);
        }

        Payment activePayment = activePayments.isEmpty() ? null : activePayments.get(0);

        // 기존 결제가 존재하고, 만료되지 않았다면 재사용
        if (activePayment != null && !activePayment.isExpired(LocalDateTime.now())) {
            log.info("[Payment] 존재하는 결제 재사용 orderId={}, paymentId={}, status={}",
                orderId, activePayment.getId(), activePayment.getStatus());
            return PaymentResponse.from(activePayment);
        }

        // 기존 결제가 존재하지만 만료된 경우, 새 결제 생성
        if (activePayment != null) {
            activePayment.expire();
            paymentRepository.save(activePayment);
            log.warn("[Payment] 만료된 결제 감지 orderId={}, paymentId={}", orderId, activePayment.getId());
            OrderSummary recoveredOrder = orderClient.getOrder(orderId);
            return createNewPayment(recoveredOrder, "EXPIRED_RECREATE");
        }
        // 기존 결제가 존재하지 않는 경우, 강제 복구 시도
        else {
            log.error("[Payment] order 는 EXECUTING 이지만 활성 Payment 가 없습니다. 강제 복구를 시도합니다. orderId={}",
                orderId);
            OrderSummary recoveredOrder = orderClient.getOrder(orderId);
            return createNewPayment(recoveredOrder, "RECOVERY_RECREATE");
        }
    }

    /**
     * 주문 정보 기준으로 새 결제를 생성한다.
     *
     * 의도:
     * 신규 생성과 복구 후 재생성을 같은 규칙으로 묶기 위해 생성 로직을 한곳에 둔다.
     * 생성 시각과 만료 시각을 함께 기록해서 이후 재진입 시 만료 판단 근거로 사용한다.
     */
    private PaymentResponse createNewPayment(OrderSummary order, String creationReason) {
        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = Payment.builder()
            .id(snowflakeIdGenerator.nextId())
            .orderId(order.orderId())
            .amount(order.totalAmount())
            .status(PaymentStatus.NOT_STARTED)
            .createdAt(createdAt)
            .expiresAt(createdAt.plusMinutes(PAYMENT_EXPIRATION_MINUTES))
            .build();

        log.info("[Payment] 결제 객체 생성 orderId={}, paymentId={}, reason={}",
            payment.getOrderId(), payment.getId(), creationReason);

        Payment savedPayment = paymentRepository.save(payment);

        log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", order.orderId(), savedPayment.getId());
        return PaymentResponse.from(savedPayment);
    }

    /**
     * 주문 id에 해당하는 결제를 생성하여 반환한다.
     *
     * @param request
     * @return psp 결제 URL
     *
     * // @Transactional : order server에 요청을 보내므로, 트랜잭션을 처리하지 않았습니다. DB 커넥션을 잡지 않기 위함입니다.
     */
    public PaymentResponse createPaymentWithUniqueKey(CreatePaymentRequest request) {
        log.info("[Payment] 결제 생성 orderId={}", request.orderId());

        OrderSummary order;
        try {
            order = orderClient.updateExecutingStatus(request.orderId());
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(request.orderId(), exception);
        }

        log.info("[Payment] 주문 상태 EXECUTING 갱신 완료 = {}", order.orderId());

        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = Payment.builder()
            .id(snowflakeIdGenerator.nextId())
            .orderId(order.orderId())
            .amount(order.totalAmount())
            .status(PaymentStatus.NOT_STARTED)
            .createdAt(createdAt)
            .expiresAt(createdAt.plusMinutes(PAYMENT_EXPIRATION_MINUTES))
            .build();

        log.info("[Payment] 결제 객체 생성 orderId={}", payment.getId());

        try {
            Payment savedPayment = paymentRepository.save(payment);
            log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", request.orderId(), savedPayment.getId());
            return PaymentResponse.from(savedPayment);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약조건 위반 등 데이터 무결성 오류 발생 시 커스텀 예외로 전환
            log.warn("[Payment] 결제 Entity 중복 생성 orderId={}", payment.getOrderId());
            throw new DuplicatePaymentException(request.orderId());
        }
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
