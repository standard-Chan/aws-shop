package jeong.awsshop.payment.application;

import java.time.LocalDateTime;
import java.util.List;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.order.domain.OrderStatus;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentAlreadyExecutingException;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentExpiredException;
import jeong.awsshop.payment.exception.PaymentNotFoundException;
import jeong.awsshop.payment.exception.PaymentRecoveryRequiredException;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.exception.TossPaymentFailureType;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCanceledException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCompletedException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyExecutingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderExpiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.TossPaymentGateway;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.TossPaymentStatus;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import jeong.awsshop.stock.application.StockReservationService;
import jeong.awsshop.stock.exception.StockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final long PAYMENT_EXPIRATION_MINUTES = 5L;
    private static final String TOSS_NOT_FOUND_PAYMENT_CODE = "NOT_FOUND_PAYMENT";
    private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES = List.of(
        PaymentStatus.NOT_STARTED,
        PaymentStatus.EXECUTING
    );

    private final OrderClient orderClient;
    private final PaymentRepository paymentRepository;
    private final TossPaymentGateway tossPaymentClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StockReservationService stockReservationService;

    /**
     * 주문 id에 해당하는 결제를 생성하여 반환한다.
     * 목표 플로우:
     * 1) Order 상태를 통해 결제 생성 진입을 점유한다.
     * 2) 이미 처리 중인 상태라면, 기존 활성 Payment를 실패 처리한다.
     * 3) 새 Payment를 생성해 실패/성공 이력을 모두 보존한다.
     *
     * 의도:
     * 같은 주문의 결제 실패 내역을 덮어쓰거나 재사용하지 않고, 매 생성 요청마다 새 Payment row를 남긴다.
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
            order = getOrderForRetry(request.orderId());
        } catch (PaymentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(request.orderId(), exception);
        }

        failActivePayments(order.orderId());
        return createNewPayment(order);
    }

    private OrderSummary getOrderForRetry(Long orderId) {
        try {
            return orderClient.getOrder(orderId);
        } catch (PaymentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(orderId, exception);
        }
    }

    private void failActivePayments(Long orderId) {
        List<Payment> activePayments = paymentRepository.findAllByOrderIdAndStatusIn(orderId, ACTIVE_PAYMENT_STATUSES);
        for (Payment activePayment : activePayments) {
            activePayment.fail();
        }
        paymentRepository.saveAll(activePayments);
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

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    /**
     * 결제 승인 요청을 처리한다. - @Transactional : 결제 승인 처리 과정에서 예외가 발생할 경우, 값의 변경이 초기화 되면 안되므로 적용하지 않았습니다. -
     * 다음 값이 초기화되면 안되는 값에 해당합니다. - paymentKey 등록 - status로 변경 (결제 진행 시 : EXECUTING , 실패 시 : FAILD)
     */
    public TossPaymentConfirmResponse confirmPayment(ConfirmPaymentRequest confirmRequest) {
        log.info("[Payment] 결제 승인 요청, 결제 정보 : paymentKey={}, 결제 id={}, 주문 id={}, 결제 금액={}",
            confirmRequest.paymentKey(),
            confirmRequest.paymentId(), confirmRequest.orderId(), confirmRequest.amount());

        Payment payment = paymentRepository.findById(confirmRequest.paymentId())
            .orElseThrow(() -> new PaymentNotFoundException(confirmRequest.paymentId()));

        OrderSummary order = getConfirmableOrder(payment.getOrderId());

        // payment 만료 여부 검증
        if (payment.isExpired(LocalDateTime.now())) {
            payment.expire();
            paymentRepository.save(payment);
            orderClient.updatePendingOrder(payment.getOrderId());
            throw new PaymentExpiredException(payment.getOrderId(), payment.getId());
        }

        // 종료 상태 결제는 승인 시도 실패로 닫지 않고, 승인 흐름 진입 전에 거부한다.
        payment.validateConfirmableStatus();
        Payment.validatePaymentKey(confirmRequest.paymentKey());

        int updatedCount = paymentRepository.startConfirmIfNotStarted(
            confirmRequest.paymentId(),
            confirmRequest.paymentKey()
        );
        if (updatedCount == 0) {
            throw new PaymentAlreadyExecutingException(confirmRequest.paymentId());
        }
        // 결제 승인 요청 시점에, Payment가 이미 다른 프로세스에서 승인 중일 수 있으므로(동시성문제), DB에서 가져온 Payment를 사용한다.
        payment = paymentRepository.findById(confirmRequest.paymentId())
            .orElseThrow(() -> new PaymentNotFoundException(confirmRequest.paymentId()));

        try {
            // 결제 로직 검증
            payment.validateOrderId(confirmRequest.orderId());
            payment.validateConfirmAmount(confirmRequest.amount());

            // 주문 상품 전체 재고 예약 처리
            reserveOrderStocks(payment.getId(), order);

            TossPaymentConfirmResponse response = tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(confirmRequest.paymentId(),
                    confirmRequest.paymentKey(), confirmRequest.amount()),
                String.valueOf(confirmRequest.paymentId()));

            return completePayment(payment, response);
        } catch (PaymentTossPaymentProcessingException exception) { // Toss confirm 실패 유형에 따라 확정 실패와 결과 불확실을 분리
            return handleTossConfirmFailure(confirmRequest, payment, exception);
        } catch (PaymentException | StockException exception) {
            // 해당 결제 실패 처리
            failPaymentAndRestore(payment);

            log.warn("[Payment] 결제 실패. {} \n paymentKey={}, orderId={}, amount={}", exception,
                confirmRequest.paymentKey(), confirmRequest.orderId(), confirmRequest.amount());
            throw new PaymentConfirmExternalException(confirmRequest.paymentId(),
                confirmRequest.paymentKey(), exception);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new PaymentException("[Payment] 알 수 없는 에러가 발생하였습니다.", e);
        }
    }

    private OrderSummary getConfirmableOrder(Long orderId) {
        OrderSummary order;
        try {
            order = orderClient.getOrder(orderId);
        } catch (PaymentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(orderId, exception);
        }

        validateConfirmableOrderStatus(order);
        return order;
    }

    private void validateConfirmableOrderStatus(OrderSummary order) {
        OrderStatus status = order.status();
        if (status == OrderStatus.EXECUTING) {
            return;
        }
        if (status == OrderStatus.COMPLETED) {
            throw new PaymentOrderAlreadyCompletedException(order.orderId(), null);
        }
        if (status == OrderStatus.CANCELED) {
            throw new PaymentOrderAlreadyCanceledException(order.orderId(), null);
        }
        if (status == OrderStatus.EXPIRED) {
            throw new PaymentOrderExpiredException(order.orderId(), null);
        }
        throw new PaymentOrderLookupException(order.orderId(),
            "결제 승인 가능한 주문 상태가 아닙니다. status=" + status);
    }

    private void reserveOrderStocks(Long paymentId, OrderSummary order) {
        List<OrderLineSummary> orderLines = order.items();
        if (orderLines.isEmpty()) {
            throw new PaymentException("[Payment] 주문 상품 정보가 없습니다. orderId=" + order.orderId());
        }

        stockReservationService.reserve(paymentId, order.orderId(), orderLines);
    }

    /** Toss confirm 실패를 확정 실패와 결과 불확실(네트워크 장애) 케이스로 나눠 후속 처리 */
    private TossPaymentConfirmResponse handleTossConfirmFailure(
        ConfirmPaymentRequest request,
        Payment payment,
        PaymentTossPaymentProcessingException exception
    ) {
        if (exception.getFailureType() == TossPaymentFailureType.CONFIRMED_FAILURE) { // Toss가 결제 실패를 확정 응답한 경우
            failPaymentAndRestore(payment);
            log.warn("[Payment] Toss 결제 승인 확정 실패. paymentId={}, paymentKey={}, tossCode={}, httpStatus={}",
                request.paymentId(), request.paymentKey(), exception.getTossErrorCode(), exception.getHttpStatus());
            throw new PaymentConfirmExternalException(request.paymentId(), request.paymentKey(), exception);
        }

        log.warn("[Payment] Toss 결제 승인 결과 불확실. 즉시 복구를 시도합니다. paymentId={}, paymentKey={}, failureType={}, tossCode={}, httpStatus={}",
            request.paymentId(), request.paymentKey(), exception.getFailureType(), exception.getTossErrorCode(),
            exception.getHttpStatus());

        return recoverUncertainTossConfirm(request, payment, exception);
    }

    /** 결과가 불확실한 Toss confirm을 조회와 1회 재시도 시도 */
    private TossPaymentConfirmResponse recoverUncertainTossConfirm(
        ConfirmPaymentRequest request,
        Payment payment,
        PaymentTossPaymentProcessingException cause
    ) {
        TossPaymentConfirmResponse lookupResult = lookupTossPaymentForRecovery(request, payment);
        if (lookupResult != null) {
            if (TossPaymentStatus.isDone(lookupResult.status())) { // Toss 조회 결과 결제 완료 상태인 경우
                return completePayment(payment, lookupResult);
            }
            if (TossPaymentStatus.isFailed(lookupResult.status())) { // Toss 조회 결과 결제 실패 상태인 경우
                failPaymentAndRestore(payment);
                throw new PaymentConfirmExternalException(request.paymentId(), request.paymentKey(), cause);
            }
        }

        return retryConfirmOnceForRecovery(request, payment);
    }

    /** paymentKey로 Toss 결제 상태를 조회하고, 조회 불가 시 후속 복구 대상으로 남긴다. */
    private TossPaymentConfirmResponse lookupTossPaymentForRecovery(
        ConfirmPaymentRequest request,
        Payment payment
    ) {
        try {
            return tossPaymentClient.getPayment(request.paymentKey());
        } catch (PaymentTossPaymentProcessingException lookupException) { // Toss 조회 실패로 결제 상태 확정 불가
            if (lookupException.hasTossErrorCode(TOSS_NOT_FOUND_PAYMENT_CODE)) {
                log.warn("[Payment] Toss 조회 결과 결제 기록이 없어 confirm 재시도를 진행합니다. paymentId={}, paymentKey={}",
                    request.paymentId(), request.paymentKey());
                return null;
            }
            log.warn("[Payment] Toss 결제 상태 조회 실패. EXECUTING 상태로 후속 복구 대상에 남깁니다. paymentId={}, paymentKey={}, tossCode={}, httpStatus={}",
                request.paymentId(), request.paymentKey(), lookupException.getTossErrorCode(),
                lookupException.getHttpStatus());
            throw new PaymentRecoveryRequiredException(payment.getOrderId());
        }
    }

    /** 같은 paymentId 멱등키로 confirm을 한 번만 재시도한다. */
    private TossPaymentConfirmResponse retryConfirmOnceForRecovery(
        ConfirmPaymentRequest request,
        Payment payment
    ) {
        try {
            TossPaymentConfirmResponse retryResponse = tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(request.paymentId(), request.paymentKey(), request.amount()),
                String.valueOf(request.paymentId())
            );
            return completePayment(payment, retryResponse);
        } catch (PaymentTossPaymentProcessingException retryException) { // confirm 재시도 실패 유형 확인
            if (retryException.getFailureType() == TossPaymentFailureType.CONFIRMED_FAILURE) { // 재시도 결과 결제 실패가 확정된 경우
                failPaymentAndRestore(payment);
                throw new PaymentConfirmExternalException(request.paymentId(), request.paymentKey(), retryException);
            }
            log.warn("[Payment] Toss confirm 재시도 결과도 불확실합니다. EXECUTING 상태로 후속 복구 대상에 남깁니다. paymentId={}, paymentKey={}, tossCode={}, httpStatus={}",
                request.paymentId(), request.paymentKey(), retryException.getTossErrorCode(),
                retryException.getHttpStatus());
            throw new PaymentRecoveryRequiredException(payment.getOrderId());
        } catch (PaymentException exception) {
            failPaymentAndRestore(payment);
            throw new PaymentConfirmExternalException(request.paymentId(), request.paymentKey(), exception);
        } catch (RuntimeException exception) {
            log.warn("[Payment] Toss confirm 재시도 중 알 수 없는 예외가 발생했습니다. EXECUTING 상태로 후속 복구 대상에 남깁니다. paymentId={}, paymentKey={}",
                request.paymentId(), request.paymentKey(), exception);
            throw new PaymentRecoveryRequiredException(payment.getOrderId());
        }
    }

    /** Toss 성공 결과를 기준으로 결제, 주문, 예약 재고를 완료 처리한다. */
    private TossPaymentConfirmResponse completePayment(Payment payment, TossPaymentConfirmResponse response) {
        // 결제 승인 완료
        payment.complete();

        log.info("[Payment] 결제 승인 완료. paymentKey={}, paymentId={}, amount={}",
            response.paymentKey(), response.orderId(), response.totalAmount());

        // Order 완료 처리
        orderClient.updateCompleteOrder(payment.getOrderId());

        stockReservationService.complete(payment.getId());
        paymentRepository.save(payment);
        return response;
    }

    /** 결제를 실패로 닫고 주문과 예약 재고를 결제 전 상태로 복구한다. */
    private void failPaymentAndRestore(Payment payment) {
        payment.fail();
        paymentRepository.save(payment);

        // Order 상태 pending 변경
        orderClient.updatePendingOrder(payment.getOrderId());

        stockReservationService.restore(payment.getId());
    }
}
