package jeong.awsshop.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.payment.toss.mode", havingValue = "real")
@Slf4j
public class TossPaymentClient implements TossPaymentGateway {

    private final RestClient tossPaymentClient;
    private final String secretKey;


    public TossPaymentClient(@Value("${TOSS_PAYMENTS_KEY}") String secretKey) {
        this.secretKey = secretKey;
        this.tossPaymentClient = RestClient.create();
    }

    /**
    * Toss Payments 서버로부터 결제 정보를 받아온다.
    */
    @Override
    public TossPaymentConfirmResponse confirm(TossPaymentConfirmRequest request) {

        try {
            return tossPaymentClient.post()
                .uri("https://api.tosspayments.com/v1/payments/confirm")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    authorizationHeader()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TossPaymentConfirmResponse.class);
        } catch (Exception e) {
            throw new PaymentTossPaymentProcessingException(request.orderId(), request.paymentKey(), e.getMessage(), e);
        }
    }

    /** Toss Payments 서버에서 paymentKey에 해당하는 결제 상태를 조회한다. */
    @Override
    public TossPaymentConfirmResponse getPayment(String paymentKey) {
        try {
            return tossPaymentClient.get()
                .uri("https://api.tosspayments.com/v1/payments/{paymentKey}", paymentKey)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    authorizationHeader()
                )
                .retrieve()
                .body(TossPaymentConfirmResponse.class);
        } catch (Exception e) {
            throw new PaymentTossPaymentProcessingException(null, paymentKey, e.getMessage(), e);
        }
    }

    private String authorizationHeader() {
        String encodedSecretKey = Base64.getEncoder()
            .encodeToString(
                (secretKey + ":")
                    .getBytes(StandardCharsets.UTF_8)
            );
        return "Basic " + encodedSecretKey;
    }

}
