package jeong.awsshop.payment.application;

import java.time.LocalDateTime;
import java.util.List;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.domain.PaymentRepository;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.order.OrderClient;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.payment.infrastructure.order.dto.OrderSummary;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.stock.application.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryService {

    private static final String TOSS_DONE_STATUS = "DONE";

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final OrderClient orderClient;
    private final StockService stockService;

    public void recoverExecutingPaymentsBefore(LocalDateTime applicationStartupTime) {
        List<Payment> recoverablePayments = paymentRepository.findAllByStatusAndCreatedAtBefore(
            PaymentStatus.EXECUTING,
            applicationStartupTime
        );

        for (Payment payment : recoverablePayments) {
            recoverPayment(payment);
        }
    }

    private void recoverPayment(Payment payment) {
        try {
            if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
                log.warn("[Payment-Recovery] paymentKey가 없어 복구를 건너뜁니다. paymentId={}, orderId={}",
                    payment.getId(), payment.getOrderId());
                return;
            }

            TossPaymentConfirmResponse tossPayment = tossPaymentClient.getPayment(payment.getPaymentKey());
            if (tossPayment != null && TOSS_DONE_STATUS.equals(tossPayment.status())) {
                recoverSuccessfulPayment(payment);
                return;
            }

            recoverFailedPayment(payment);
        } catch (RuntimeException exception) {
            log.error("[Payment-Recovery] 결제 복구에 실패했습니다. paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId(), exception);
        }
    }

    private void recoverSuccessfulPayment(Payment payment) {
        payment.complete();
        orderClient.updateCompleteOrder(payment.getOrderId());
        paymentRepository.save(payment);
        log.info("[Payment-Recovery] 결제 성공 상태를 복구했습니다. paymentId={}, orderId={}",
            payment.getId(), payment.getOrderId());
    }

    private void recoverFailedPayment(Payment payment) {
        payment.fail();
        paymentRepository.save(payment);

        orderClient.updatePendingOrder(payment.getOrderId());
        OrderSummary order = orderClient.getOrder(payment.getOrderId());
        restoreReservedStocks(order.items());

        log.info("[Payment-Recovery] 결제 실패 상태를 복구했습니다. paymentId={}, orderId={}",
            payment.getId(), payment.getOrderId());
    }

    private void restoreReservedStocks(List<OrderLineSummary> reservedLines) {
        for (int index = reservedLines.size() - 1; index >= 0; index--) {
            OrderLineSummary line = reservedLines.get(index);
            try {
                stockService.increase(line.productId(), line.quantity());
            } catch (RuntimeException restoreException) {
                log.error("[Payment-Recovery] 예약 재고 복구 실패. productId={}, quantity={}",
                    line.productId(), line.quantity(), restoreException);
            }
        }
    }
}
