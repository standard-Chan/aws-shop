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

    public void reset() {
        confirmCount.set(0);
        confirmRequests.clear();
    }

    public int confirmCount() {
        return confirmCount.get();
    }

    public List<TossPaymentConfirmRequest> confirmRequests() {
        return List.copyOf(confirmRequests);
    }
}
