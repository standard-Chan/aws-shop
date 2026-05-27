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
     * 목표 플로우:
     * 1) Order 상태를 통해 결제 생성 진입을 점유한다.
     * 2) 이미 처리 중인 상태라면, 기존 Payment를 조회해 만료 여부를 판단한다.
     * 3) 만료되지 않았다면 기존 Payment를 반환한다.
     * 4) 만료되었거나 처리 중 Payment가 없으면 장애 복구 후 새 Payment를 생성한다.
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
            // 1. 주문 상태를 EXECUTING 으로 전환하며 결제 생성 진입을 점유한다.
            // 정상적으로 점유되면 현재 order 기준 처리 중 Payment가 없다고 간주하고 새 Payment를 생성한다.
            order = orderClient.updateExecutingStatus(request.orderId());
            log.info("[Payment] 주문 상태 EXECUTING 갱신 완료 = {}", order.orderId());
        } catch (PaymentOrderAlreadyExecutingException exception) {
            // TODO:
            // 2. order 가 이미 EXECUTING 이라면 바로 중복 예외로 끝내지 말고, (NOT_STARTED, EXECUTING) 상태의 Payment 를 다시 조회해야 한다.
            // 3. 해당 Payment 가 존재하면 만료 여부를 판단한다. 만료 여부는 Payment의 expiredAt 필드와 현재시각을 비교해서 확인한다.
            // 현재는 expiredAt 필드가 없으므로, expiredAt 필드를 Entity에 추가하여 처리한다.
            // 4. 만료되지 않았으면 기존 Payment 를 그대로 반환한다.
            // 5. 만료되었으면 order/payment 상태를 복구한 뒤 새 Payment 를 생성한다.
            // 6. 처리 중 Payment 가 없으면 장애 상황으로 보고 로그를 남기고, 강제 복구 이후 새 Payment 를 생성한다.
            // 7. Order는 이미 EXECUTING 상태이므로, 그대로 EXECUTING으로 유지해두고, Payment 를 새롭게 생성한다.
            Payment executingPayment = paymentRepository.findByOrderIdAndStatus(request.orderId(), PaymentStatus.EXECUTING)
                .orElseThrow(() -> new DuplicatePaymentException(request.orderId()));

            log.info("[Payment] 진행 중 결제 반환 orderId={}, paymentId={}",
                request.orderId(), executingPayment.getId());

            return new PaymentResponse(
                String.valueOf(executingPayment.getId()),
                executingPayment.getOrderId(),
                executingPayment.getStatus(),
                executingPayment.getAmount()
            );
        } catch (RuntimeException exception) {
            throw new PaymentOrderLookupException(request.orderId(), exception);
        }


        // TODO:
        // 만료된 기존 Payment 의 재생성인지, 최초 생성인지 로그/메트릭으로 구분할 수 있게 생성 사유를 남긴다.
        // 만료 판단 기준이 들어오면 Payment 생성 전에 복구 단계와 함께 묶어서 기록한다.
        // 결제 Entity 생성
        // 만료 필드를 추가한 뒤, expiredAt 필드와 현재 시각을 비교하여 만료 여부를 판단한다. (5분으로 설정)
        Payment payment = Payment.builder()
            .id(snowflakeIdGenerator.nextId())
            .orderId(request.orderId())
            .amount(order.totalAmount())
            .status(PaymentStatus.NOT_STARTED)
            .build();

        log.info("[Payment] 결제 객체 생성 orderId={}", payment.getId());

        Payment savedPayment = paymentRepository.save(payment);
        // 응답 생성
        PaymentResponse response = new PaymentResponse(String.valueOf(savedPayment.getId()),
            savedPayment.getOrderId(),
            savedPayment.getStatus(), savedPayment.getAmount());

        log.info("[Payment] 결제 Entity 생성 주문번호={}, id={}", request.orderId(), savedPayment.getId());

        return response;
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
            log.info("[Payment] 주문 상태 EXECUTING 갱신 완료 = {}", order.orderId());
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

        log.info("[Payment] 결제 객체 생성 orderId={}", payment.getId());

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
