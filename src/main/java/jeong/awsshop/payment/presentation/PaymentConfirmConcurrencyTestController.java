package jeong.awsshop.payment.presentation;

import jeong.awsshop.payment.application.PaymentConfirmConcurrencyFixtureService;
import jeong.awsshop.payment.application.PaymentConfirmConcurrencyFixtureService.PaymentConfirmConcurrencyFixtureResponse;
import jeong.awsshop.payment.application.PaymentConfirmConcurrencyFixtureService.PaymentConfirmConcurrencyResultResponse;
import jeong.awsshop.payment.application.PaymentConfirmConcurrencyFixtureService.PaymentConfirmConcurrencyTossStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@ConditionalOnProperty(name = "app.payment.toss.mode", havingValue = "mock")
@RequestMapping("/test/payment-confirm-concurrency")
@RequiredArgsConstructor
public class PaymentConfirmConcurrencyTestController {

    private final PaymentConfirmConcurrencyFixtureService fixtureService;

    @PostMapping("/fixtures")
    public PaymentConfirmConcurrencyFixtureResponse createFixture() {
        return fixtureService.createFixture();
    }

    @GetMapping("/fixtures/{paymentId}/result")
    public PaymentConfirmConcurrencyResultResponse getResult(@PathVariable Long paymentId) {
        return fixtureService.getResult(paymentId);
    }

    @GetMapping("/toss-stats")
    public PaymentConfirmConcurrencyTossStatsResponse getTossStats() {
        return fixtureService.getTossStats();
    }
}
