package jeong.awsshop.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class TossPaymentClient {

    private final RestClient tossPaymentClient;
    private final String secretKey;


    public TossPaymentClient(@Value("${TOSS_PAYMENTS_KEY}") String secretKey) {
        this.secretKey = secretKey;
        this.tossPaymentClient = RestClient.create();
    }

    /**
    * Toss Payments 서버로부터 결제 정보를 받아온다.
    */
    public TossPaymentConfirmResponse confirm(TossPaymentConfirmRequest request) {
        String encodedSecretKey = Base64.getEncoder()
            .encodeToString(
                (secretKey + ":")
                    .getBytes(StandardCharsets.UTF_8)
            );

        return tossPaymentClient.post()
            .uri("https://api.tosspayments.com/v1/payments/confirm")
            .header(
                HttpHeaders.AUTHORIZATION,
                "Basic " + encodedSecretKey
            )
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(TossPaymentConfirmResponse.class);
    }

}
