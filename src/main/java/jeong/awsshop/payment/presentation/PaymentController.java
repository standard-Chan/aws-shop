package jeong.awsshop.payment.presentation;

import jeong.awsshop.payment.application.PaymentService;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping()
    public String getPaymentUrl(@RequestBody CreatePaymentRequest request) {
        log.info("[Payment] 결제 생성 및 URL 요청. orderId={}", request.orderId());

        String redirectUrl = paymentService.getPspRedirectionUrl(request.orderId());
        return "redirect:"+redirectUrl;
    }

}
