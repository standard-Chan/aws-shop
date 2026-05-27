package jeong.awsshop.payment.presentation;

import jeong.awsshop.payment.application.PaymentService;
import jeong.awsshop.payment.exception.DuplicatePaymentException;
import jeong.awsshop.payment.exception.PaymentExpiredException;
import jeong.awsshop.payment.exception.PaymentRecoveryRequiredException;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import jeong.awsshop.payment.presentation.dto.ConfirmPaymentRequest;
import jeong.awsshop.payment.presentation.dto.CreatePaymentRequest;
import jeong.awsshop.payment.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping()
    public PaymentResponse createPayment(@RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @PostMapping("/confirm")
    public TossPaymentConfirmResponse confirmPayment(
        @RequestBody ConfirmPaymentRequest request) {
        return paymentService.confirmPayment(request);
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicatePaymentException.class)
    public String handleDuplicatePayment(DuplicatePaymentException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.GONE)
    @ExceptionHandler(PaymentExpiredException.class)
    public String handleExpiredPayment(PaymentExpiredException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(PaymentRecoveryRequiredException.class)
    public String handlePaymentRecoveryRequired(PaymentRecoveryRequiredException ex) {
        return ex.getMessage();
    }
}
