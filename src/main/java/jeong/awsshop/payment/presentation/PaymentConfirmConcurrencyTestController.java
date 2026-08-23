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

    /** 결제 승인 동시성 HTTP 검증에 사용할 fixture를 생성한다. */
    @PostMapping("/fixtures")
    public PaymentConfirmConcurrencyFixtureResponse createFixture() {
        return fixtureService.createFixture();
    }

    /** 특정 fixture 결제의 최종 Payment, Order, 재고 상태를 조회한다. */
    @GetMapping("/fixtures/{paymentId}/result")
    public PaymentConfirmConcurrencyResultResponse getResult(@PathVariable Long paymentId) {
        return fixtureService.getResult(paymentId);
    }

    /** mock Toss confirm 호출 수와 요청 이력을 조회한다. */
    @GetMapping("/toss-stats")
    public PaymentConfirmConcurrencyTossStatsResponse getTossStats() {
        return fixtureService.getTossStats();
    }
}
