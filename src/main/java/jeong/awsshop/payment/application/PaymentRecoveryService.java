package jeong.awsshop.payment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.exception.TossPaymentFailureType;
import jeong.awsshop.payment.infrastructure.TossPaymentGateway;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.tosspayment.TossPaymentStatus;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.stock.application.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryService {

    private static final String TOSS_NOT_FOUND_PAYMENT_CODE = "NOT_FOUND_PAYMENT";
    private static final BigDecimal TOSS_KRW_EXCHANGE_RATE = new BigDecimal("1400");

    private final PaymentRepository paymentRepository;
    private final TossPaymentGateway tossPaymentClient;
    private final OrderClient orderClient;
    private final StockReservationService stockReservationService;

    /** 
     * 서버 비정상 종료로 인한 결제 상태 불일치 처리
     * 서버 실행 시작 시각 기준으로, 이전에 결제 상태 = EXECUTING 인 결제를 조회하여, toss 결제 상태와 일치시킨다.
     * 서버 실행 시각을 기준으로 하는 이유: 신규 결제 진행 중인 EXECUTING 결제는 제외하기 위함
    */
    public void recoverExecutingPaymentsBefore(LocalDateTime applicationStartupTime) {
        List<Payment> recoverablePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        );

        for (Payment payment : recoverablePayments) {
            recoverPayment(payment);
        }
    }

    /** Toss Payments 결제 정보를 조회하여, 로컬 DB 결제 상태를 toss 결제 상태와 통일시킨다. */
    private void recoverPayment(Payment payment) {
        try {
            if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
                log.warn("[Payment-Recovery] paymentKey가 없어 복구를 건너뜁니다. paymentId={}, orderId={}",
                    payment.getId(), payment.getOrderId());
                return;
            }

            if (!stockReservationService.hasAnyReservation(payment.getId())) {
                recoverFailedPaymentWithoutReservedStock(payment);
                return;
            }

            TossPaymentConfirmResponse tossPayment = tossPaymentClient.getPayment(payment.getPaymentKey());
            if (tossPayment != null && TossPaymentStatus.isDone(tossPayment.status())) {
                recoverSuccessfulPayment(payment);
                return;
            }
            if (tossPayment != null && TossPaymentStatus.isFailed(tossPayment.status())) {
                recoverFailedPayment(payment);
                return;
            }

            retryConfirmOnce(payment);
        } catch (PaymentTossPaymentProcessingException exception) { // Toss 조회 실패로 결제 상태 확정 불가
            if (exception.hasTossErrorCode(TOSS_NOT_FOUND_PAYMENT_CODE)) {
                retryConfirmOnce(payment);
                return;
            }
            log.error("[Payment-Recovery] Toss 조회 결과를 확정할 수 없어 EXECUTING 상태를 유지합니다. paymentId={}, orderId={}, tossCode={}, httpStatus={}",
                payment.getId(), payment.getOrderId(), exception.getTossErrorCode(), exception.getHttpStatus(), exception);
        } catch (RuntimeException exception) {
            log.error("[Payment-Recovery] 결제 복구에 실패했습니다. paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId(), exception);
        }
    }

    /** 정상 결제 처리 */
    private void recoverSuccessfulPayment(Payment payment) {
        payment.complete();
        orderClient.updateCompleteOrder(payment.getOrderId());
        stockReservationService.complete(payment.getId());
        paymentRepository.save(payment);
        log.info("[Payment-Recovery] 결제 성공 상태를 복구했습니다. paymentId={}, orderId={}",
            payment.getId(), payment.getOrderId());
    }

    /** 재고 예약 전 종료된 결제 실패 처리 */
    private void recoverFailedPaymentWithoutReservedStock(Payment payment) {
        payment.fail();
        paymentRepository.save(payment);

        orderClient.updatePendingOrder(payment.getOrderId());

        log.info("[Payment-Recovery] 예약 재고 없이 중단된 결제 상태를 실패로 복구했습니다. paymentId={}, orderId={}",
            payment.getId(), payment.getOrderId());
    }

    /** 결제 실패 처리 */
    private void recoverFailedPayment(Payment payment) {
        payment.fail();
        paymentRepository.save(payment);

        orderClient.updatePendingOrder(payment.getOrderId());
        stockReservationService.restore(payment.getId());

        log.info("[Payment-Recovery] 결제 실패 상태를 복구했습니다. paymentId={}, orderId={}",
            payment.getId(), payment.getOrderId());
    }

    /** 후속 복구에서 미확정 결제를 같은 paymentId 멱등키로 한 번만 다시 승인 요청한다. */
    private void retryConfirmOnce(Payment payment) {
        try {
            TossPaymentConfirmResponse response = tossPaymentClient.confirm(
                new TossPaymentConfirmRequest(
                    payment.getId(),
                    payment.getPaymentKey(),
                    payment.getAmount().multiply(TOSS_KRW_EXCHANGE_RATE)
                ),
                String.valueOf(payment.getId())
            );
            if (response != null && TossPaymentStatus.isDone(response.status())) {
                recoverSuccessfulPayment(payment);
                return;
            }
            if (response != null && TossPaymentStatus.isFailed(response.status())) {
                recoverFailedPayment(payment);
                return;
            }
            log.warn("[Payment-Recovery] Toss confirm 재시도 결과를 확정할 수 없어 EXECUTING 상태를 유지합니다. paymentId={}, orderId={}, status={}",
                payment.getId(), payment.getOrderId(), response == null ? null : response.status());
        } catch (PaymentTossPaymentProcessingException exception) { // 복구 confirm 재시도 실패 유형 확인
            if (exception.getFailureType() == TossPaymentFailureType.CONFIRMED_FAILURE) { // 재시도 결과 결제 실패가 확정된 경우
                recoverFailedPayment(payment);
                return;
            }
            log.warn("[Payment-Recovery] Toss confirm 재시도 결과도 불확실해 EXECUTING 상태를 유지합니다. paymentId={}, orderId={}, tossCode={}, httpStatus={}",
                payment.getId(), payment.getOrderId(), exception.getTossErrorCode(), exception.getHttpStatus());
        } catch (RuntimeException exception) {
            log.error("[Payment-Recovery] Toss confirm 재시도 중 예외가 발생해 EXECUTING 상태를 유지합니다. paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId(), exception);
        }
    }
}
