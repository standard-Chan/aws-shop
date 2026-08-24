package jeong.awsshop.payment.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmRequest;
import jeong.awsshop.payment.infrastructure.tosspayment.dto.TossPaymentConfirmResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payment.toss.mode", havingValue = "mock")
public class MockTossPaymentClient implements TossPaymentGateway {

    private static final String DONE_STATUS = "DONE";
    private static final String MOCK_METHOD = "CARD";

    private final AtomicInteger confirmCount = new AtomicInteger();
    private final CopyOnWriteArrayList<TossPaymentConfirmRequest> confirmRequests = new CopyOnWriteArrayList<>();

    /** Toss confirm 성공 응답을 반환하고 호출 횟수와 요청 이력을 기록한다. */
    @Override
    public TossPaymentConfirmResponse confirm(TossPaymentConfirmRequest request) {
        confirmCount.incrementAndGet();
        confirmRequests.add(request);

        OffsetDateTime now = OffsetDateTime.now();
        return new TossPaymentConfirmResponse(
            request.paymentKey(),
            String.valueOf(request.orderId()),
            MOCK_METHOD,
            DONE_STATUS,
            request.amount().longValue(),
            now,
            now
        );
    }

    /** 복구 로직에서 조회하는 Toss 결제 상태를 항상 DONE으로 반환한다. */
    @Override
    public TossPaymentConfirmResponse getPayment(String paymentKey) {
        OffsetDateTime now = OffsetDateTime.now();
        return new TossPaymentConfirmResponse(
            paymentKey,
            null,
            MOCK_METHOD,
            DONE_STATUS,
            0L,
            now,
            now
        );
    }

    /** 병렬 검증 fixture 생성 시 이전 mock 호출 이력을 초기화한다. */
    public void reset() {
        confirmCount.set(0);
        confirmRequests.clear();
    }

    /** 현재까지 mock Toss confirm이 호출된 횟수를 반환한다. */
    public int confirmCount() {
        return confirmCount.get();
    }

    /** 현재까지 mock Toss confirm으로 들어온 요청 이력을 복사해 반환한다. */
    public List<TossPaymentConfirmRequest> confirmRequests() {
        return List.copyOf(confirmRequests);
    }
}
