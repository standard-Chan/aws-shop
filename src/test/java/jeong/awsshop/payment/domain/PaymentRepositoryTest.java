package jeong.awsshop.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동시에 결제 승인 시작을 시도해도 하나의 요청만 EXECUTING 전환에 성공해야 한다")
    void should_start_only_one_confirm_when_concurrent_requests_try_cas() throws Exception {
        // Given
        paymentRepository.saveAndFlush(payment(1L, PaymentStatus.NOT_STARTED));
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        // When
        for (int index = 0; index < threadCount; index++) {
            int requestIndex = index;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return paymentRepository.startConfirmIfNotStarted(
                    1L,
                    "payment-key-" + requestIndex
                );
            }));
        }
        ready.await();
        start.countDown();

        int successCount = 0;
        for (Future<Integer> future : futures) {
            successCount += future.get();
        }
        executor.shutdown();

        // Then
        Payment payment = paymentRepository.findById(1L).orElseThrow();
        assertThat(successCount).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXECUTING);
        assertThat(payment.getPaymentKey()).startsWith("payment-key-");
    }

    private Payment payment(Long id, PaymentStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return Payment.builder()
            .id(id)
            .orderId(123L)
            .status(status)
            .amount(new BigDecimal("100.00"))
            .createdAt(now)
            .expiresAt(now.plusMinutes(5))
            .build();
    }
}
