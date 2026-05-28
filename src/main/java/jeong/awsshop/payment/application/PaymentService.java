package jeong.awsshop.payment.application;

import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.DuplicatePaymentException;
import jeong.awsshop.payment.exception.PaymentConfirmExternalException;
import jeong.awsshop.payment.exception.PaymentException;
import jeong.awsshop.payment.exception.PaymentNotFoundException;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.exception.infrastructure.PaymentOrderLookupException;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
     *
     * // @Transactional : order server에 요청을 보내므로, 트랜잭션을 처리하지 않았습니다. DB 커넥션을 잡지 않기 위함입니다.
     */
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("[Payment] 결제 생성 orderId={}", request.orderId());

        OrderSummary order;
        try {
            // 주문 정보 조회
            order = orderClient.getOrder(request.orderId());
            log.info("[Payment] 주문 정보 조회 완료 = {}", order.orderId());
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

        log.info("[Payment] 결제 객체 생성 paymentId={}, orderId={}", payment.getId(), payment.getOrderId());

        try {
            Payment savedPayment = paymentRepository.save(payment);
            // 응답 생성
            PaymentResponse response = new PaymentResponse(String.valueOf(savedPayment.getId()),
                savedPayment.getOrderId(),
                savedPayment.getStatus(), savedPayment.getAmount());

            log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", request.orderId(), savedPayment.getId());

            return response;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약조건 위반 등 데이터 무결성 오류 발생 시 커스텀 예외로 전환
            log.warn("[Payment] 결제 Entity 중복 생성 orderId={}", payment.getOrderId());
            throw new DuplicatePaymentException(request.orderId(), e);
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
        } catch (PaymentException exception) {
            failPayment(payment, exception);
            throw exception;
        }

        // TODO : 주문 상태 검증
        // 주문 상태가 COMPLETED인 경우, 결제 승인 요청이 들어오면, 주문이 이미 완료된 상태이므로, 결제 승인 요청을 실패 처리한다.
        // (실제 서비스에서는 주문 상태가 COMPLETED인 경우, 결제 승인 요청이 들어오지 않도록 프론트엔드에서 막는 것이 좋다.)

        try {
            // toss 결제 승인 요청
            TossPaymentConfirmResponse response = tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(confirmRequest.paymentId(),
                    confirmRequest.paymentKey(), confirmRequest.amount()));

            // 결제 승인 완료
            payment.complete();
            paymentRepository.save(payment);

            log.info("[Payment] 결제 승인 완료. paymentKey={}, paymentId={}, amount={}",
                response.paymentKey(), response.orderId(), response.totalAmount());

            // Order 완료 처리
            orderClient.updateCompleteOrder(payment.getOrderId());

            // TODO : 재고 감소 처리

            return response;
        } catch (PaymentTossPaymentProcessingException exception) {
            failPayment(payment, exception);
            throw new PaymentConfirmExternalException(confirmRequest.paymentId(),
                confirmRequest.paymentKey(), exception);
        } catch (Exception e) {
            log.error("[Payment] 결제 승인 처리 중 알 수 없는 에러가 발생했습니다. paymentId={}, orderId={}",
                confirmRequest.paymentId(), confirmRequest.orderId(), e);
            throw new PaymentException("[Payment] 알 수 없는 에러가 발생하였습니다.", e);
        }
    }

    private void failPayment(Payment payment, Exception cause) {
        try {
            if (payment.getStatus() == PaymentStatus.EXECUTING) {
                payment.fail();
                paymentRepository.save(payment);
            }
        } catch (RuntimeException rollbackException) {
            cause.addSuppressed(rollbackException);
            log.error("[Payment] 결제 실패 상태 저장에 실패했습니다. paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId(), rollbackException);
        }

        try {
            orderClient.updatePendingOrder(payment.getOrderId());
        } catch (RuntimeException rollbackException) {
            cause.addSuppressed(rollbackException);
            log.error("[Payment] 주문 상태를 PENDING 으로 복구하지 못했습니다. paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId(), rollbackException);
        }

        log.warn("[Payment] 결제 실패 처리 완료. paymentId={}, orderId={}, status={}",
            payment.getId(), payment.getOrderId(), payment.getStatus(), cause);
    }
}
