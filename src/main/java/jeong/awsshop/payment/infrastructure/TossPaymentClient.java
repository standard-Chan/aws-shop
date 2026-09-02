package jeong.awsshop.payment.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jeong.awsshop.payment.exception.PaymentTossPaymentProcessingException;
import jeong.awsshop.payment.exception.TossPaymentFailureType;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentErrorResponse;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
@ConditionalOnProperty(name = "app.payment.toss.mode", havingValue = "real")
@Slf4j
public class TossPaymentClient implements TossPaymentGateway {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";
    private static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";
    private static final String NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";

    private final RestClient tossPaymentClient;
    private final String secretKey;
    private final ObjectMapper objectMapper;


    public TossPaymentClient(@Value("${TOSS_PAYMENTS_KEY}") String secretKey) {
        this(secretKey, RestClient.create(), new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    TossPaymentClient(String secretKey, RestClient tossPaymentClient, ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        this.tossPaymentClient = tossPaymentClient;
        this.objectMapper = objectMapper;
    }

    /**
    * Toss Payments 서버로부터 결제 승인을 요청한다.
     * @param request 결제 승인 요청 정보
    */
    @Override
    public TossPaymentConfirmResponse confirm(TossPaymentConfirmRequest request, String idempotencyKey) {

        try {
            return tossPaymentClient.post()
                .uri("https://api.tosspayments.com/v1/payments/confirm")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    authorizationHeader()
                )
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TossPaymentConfirmResponse.class);
        } catch (RestClientResponseException exception) { // Toss가 HTTP 에러 응답과 에러 코드 반환
            throw tossResponseException("confirm", request.orderId(), request.paymentKey(), exception);
        } catch (ResourceAccessException exception) { // timeout, 연결 실패 등 응답 수신 여부 확인 불가
            throw uncertainException("confirm", request.orderId(), request.paymentKey(), exception);
        } catch (RestClientException exception) { // 응답 파싱 실패 등 RestClient 처리 중 발생한 불확실 오류
            throw uncertainException("confirm", request.orderId(), request.paymentKey(), exception);
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
        } catch (RestClientResponseException exception) {
            throw tossResponseException("lookup", null, paymentKey, exception);
        } catch (ResourceAccessException exception) {
            throw uncertainException("lookup", null, paymentKey, exception);
        } catch (RestClientException exception) {
            throw uncertainException("lookup", null, paymentKey, exception);
        }
    }

    /** Toss 에러 응답 본문을 파싱하고 실패 유형을 분류해 도메인 예외로 변환한다. */
    private PaymentTossPaymentProcessingException tossResponseException(
        String operation,
        Long paymentId,
        String paymentKey,
        RestClientResponseException exception
    ) {
        TossPaymentErrorResponse errorResponse = parseErrorResponse(exception);
        String code = errorResponse.code();
        String message = errorResponse.message() == null ? exception.getMessage() : errorResponse.message();
        int statusCode = exception.getStatusCode().value();
        TossPaymentFailureType failureType = classify(operation, statusCode, code);

        log.warn("[Payment-Toss] Toss API error. operation={}, failureType={}, httpStatus={}, code={}, message={}, paymentId={}, paymentKey={}",
            operation, failureType, statusCode, code, message, paymentId, paymentKey);

        return new PaymentTossPaymentProcessingException(
            paymentId,
            paymentKey,
            code,
            statusCode,
            failureType,
            message,
            exception
        );
    }

    /** 네트워크/클라이언트 처리 오류를 결과 불확실 예외로 변환한다. */
    private PaymentTossPaymentProcessingException uncertainException(
        String operation,
        Long paymentId,
        String paymentKey,
        RestClientException exception
    ) {
        log.warn("[Payment-Toss] Toss API result is uncertain. operation={}, message={}, paymentId={}, paymentKey={}",
            operation, exception.getMessage(), paymentId, paymentKey, exception);

        return new PaymentTossPaymentProcessingException(
            paymentId,
            paymentKey,
            null,
            null,
            TossPaymentFailureType.UNCERTAIN,
            exception.getMessage(),
            exception
        );
    }

    private TossPaymentErrorResponse parseErrorResponse(RestClientResponseException exception) {
        try {
            return objectMapper.readValue(exception.getResponseBodyAsString(), TossPaymentErrorResponse.class);
        } catch (Exception ignored) {
            return new TossPaymentErrorResponse(null, exception.getMessage());
        }
    }

    private TossPaymentFailureType classify(String operation, int httpStatus, String tossErrorCode) {
        if (httpStatus >= 500) {
            return TossPaymentFailureType.UNCERTAIN;
        }
        if (ALREADY_PROCESSED_PAYMENT.equals(tossErrorCode)) {
            return TossPaymentFailureType.ALREADY_PROCESSED;
        }
        if (PROVIDER_ERROR.equals(tossErrorCode) || IDEMPOTENT_REQUEST_PROCESSING.equals(tossErrorCode)) {
            return TossPaymentFailureType.UNCERTAIN;
        }
        if ("lookup".equals(operation) && httpStatus == 404 && NOT_FOUND_PAYMENT.equals(tossErrorCode)) {
            return TossPaymentFailureType.UNCERTAIN;
        }
        return TossPaymentFailureType.CONFIRMED_FAILURE;
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
