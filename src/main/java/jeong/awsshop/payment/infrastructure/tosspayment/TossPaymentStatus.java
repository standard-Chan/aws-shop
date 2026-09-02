package jeong.awsshop.payment.infrastructure.tosspayment;

import java.util.Arrays;

public enum TossPaymentStatus {

    DONE,
    CANCELED,
    ABORTED,
    EXPIRED,
    UNKNOWN;

    /** Toss 응답 문자열을 내부에서 비교하기 쉬운 enum으로 변환한다. */
    public static TossPaymentStatus from(String status) {
        if (status == null || status.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
            .filter(value -> value != UNKNOWN)
            .filter(value -> value.name().equals(status))
            .findFirst()
            .orElse(UNKNOWN);
    }

    /** Toss 결제가 승인 완료 상태인지 확인한다. */
    public static boolean isDone(String status) {
        return from(status) == DONE;
    }

    /** Toss 결제가 실패로 닫힌 상태인지 확인한다. */
    public static boolean isFailed(String status) {
        TossPaymentStatus tossPaymentStatus = from(status);
        return tossPaymentStatus == CANCELED
            || tossPaymentStatus == ABORTED
            || tossPaymentStatus == EXPIRED;
    }
}
