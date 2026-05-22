package jeong.awsshop.payment.presentation;

import jeong.awsshop.payment.application.PaymentService;
import jeong.awsshop.payment.infrastructure.TossPaymentClient;
import jeong.awsshop.payment.infrastructure.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final TossPaymentClient tossPaymentClient;

    @PostMapping()
    public String getPaymentUrl(@RequestBody CreatePaymentRequest request) {
        log.info("[Payment] 결제 생성 및 URL 요청. orderId={}", request.orderId());

        String redirectUrl = paymentService.getPspRedirectionUrl(request.orderId());
        return "redirect:"+redirectUrl;
    }

    @PostMapping("/confirm")
    public TossPaymentConfirmResponse confirmPayment(@RequestBody TossPaymentConfirmRequest request) {
        log.info("[Payment] 결제 승인 요청");
        TossPaymentConfirmResponse response =  tossPaymentClient.confirm(request);
        log.info("[Payment] 결제 승인 완료. paymentKey={}, orderId={}, amount={}",
            response.paymentKey(), response.orderId(), response.totalAmount());

        return response;
    }

}
