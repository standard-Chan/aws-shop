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
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCanceledException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyCompletedException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderAlreadyExecutingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderExpiredException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.TossPaymentGateway;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import jeong.awsshop.stock.application.StockService;
import jeong.awsshop.stock.exception.StockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TossPaymentGateway tossPaymentClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StockService stockService;

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
        log.info("[Payment] 결제 승인 요청, 결제 정보 : 결제 id={}, 주문 id={}, 결제 금액={}",
            confirmRequest.paymentKey(),
            confirmRequest.orderId(), confirmRequest.amount());

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
        payment.start(confirmRequest.paymentKey());

        List<OrderLineSummary> reservedLines = List.of();

        try {
            // 결제 로직 검증
            payment.validateOrderId(confirmRequest.orderId());
            payment.validateConfirmAmount(confirmRequest.amount());

            // 주문 상품 전체 재고 예약 처리
            reservedLines = reserveOrderStocks(order);

            TossPaymentConfirmResponse response = tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(confirmRequest.paymentId(),
                    confirmRequest.paymentKey(), confirmRequest.amount()));

            // 결제 승인 완료
            payment.complete();

            log.info("[Payment] 결제 승인 완료. paymentKey={}, paymentId={}, amount={}",
                response.paymentKey(), response.orderId(), response.totalAmount());

            // Order 완료 처리
            orderClient.updateCompleteOrder(payment.getOrderId());

            paymentRepository.save(payment);
            return response;
        } catch (PaymentException | StockException exception) {
            // 해당 결제 실패 처리
            payment.fail();
            paymentRepository.save(payment);

            // Order 상태 pending 변경
            orderClient.updatePendingOrder(payment.getOrderId());

            restoreReservedStocks(reservedLines);

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

    private List<OrderLineSummary> reserveOrderStocks(OrderSummary order) {
        List<OrderLineSummary> orderLines = order.items();
        if (orderLines.isEmpty()) {
            throw new PaymentException("[Payment] 주문 상품 정보가 없습니다. orderId=" + order.orderId());
        }

        List<OrderLineSummary> reservedLines = new java.util.ArrayList<>();
        try {
            for (OrderLineSummary line : orderLines) {
                stockService.decrease(line.productId(), line.quantity());
                reservedLines.add(line);
            }
        } catch (PaymentException | StockException exception) {
            restoreReservedStocks(reservedLines);
            throw exception;
        }

        return reservedLines;
    }

    private void restoreReservedStocks(List<OrderLineSummary> reservedLines) {
        for (int index = reservedLines.size() - 1; index >= 0; index--) {
            OrderLineSummary line = reservedLines.get(index);
            try {
                stockService.increase(line.productId(), line.quantity());
            } catch (RuntimeException restoreException) {
                log.error("[Payment] 예약 재고 복구 실패. productId={}, quantity={}",
                    line.productId(), line.quantity(), restoreException);
            }
        }
    }
}
