package jeong.awsshop.payment.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRecoveryStartupRunner {

    private final PaymentRecoveryService paymentRecoveryService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverExecutingPayments() {
        paymentRecoveryService.recoverExecutingPaymentsBefore(LocalDateTime.now());
    }
}
