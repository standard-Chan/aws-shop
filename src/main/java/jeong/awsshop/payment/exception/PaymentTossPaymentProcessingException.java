package jeong.awsshop.payment.exception;

public class PaymentTossPaymentProcessingException extends PaymentException {

    private final Long paymentId;
    private final String paymentKey;
    private final String tossErrorCode;
    private final Integer httpStatus;
    private final TossPaymentFailureType failureType;

    public PaymentTossPaymentProcessingException(Long paymentId, String paymentKey, String message, Throwable cause) {
        this(paymentId, paymentKey, null, null, TossPaymentFailureType.UNCERTAIN, message, cause);
    }

    public PaymentTossPaymentProcessingException(
        Long paymentId,
        String paymentKey,
        String tossErrorCode,
        Integer httpStatus,
        TossPaymentFailureType failureType,
        String message,
        Throwable cause
    ) {
        super("[Payment] toss payment 요청에 실패했습니다. message=" + message
                + ", failureType=" + failureType
                + ", httpStatus=" + httpStatus
                + ", tossErrorCode=" + tossErrorCode
                + ", paymentId=" + paymentId
                + ", paymentKey=" + paymentKey,
            cause);
        this.paymentId = paymentId;
        this.paymentKey = paymentKey;
        this.tossErrorCode = tossErrorCode;
        this.httpStatus = httpStatus;
        this.failureType = failureType;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public String getTossErrorCode() {
        return tossErrorCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public TossPaymentFailureType getFailureType() {
        return failureType;
    }

    public boolean hasTossErrorCode(String code) {
        return code != null && code.equals(tossErrorCode);
    }
}
