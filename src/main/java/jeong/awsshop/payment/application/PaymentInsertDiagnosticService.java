package jeong.awsshop.payment.application;

import java.math.BigDecimal;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.domain.PaymentStatus;
import jeong.awsshop.payment.exception.DuplicatePaymentException;
import jeong.awsshop.payment.presentation.dto.PaymentInsertDiagnosticRequest;
import jeong.awsshop.payment.presentation.dto.PaymentInsertDiagnosticResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInsertDiagnosticService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentInsertDiagnosticResponse createInsertOnlyPayment(PaymentInsertDiagnosticRequest request) {
        long paymentId = snowflakeIdGenerator.nextId();
        long holdBeforeCommitMillis = normalizeHoldBeforeCommitMillis(request.holdBeforeCommitMillis());

        log.info("[Payment-Diagnostic] insert-only 결제 생성 시작 paymentId={}, orderId={}, holdBeforeCommitMillis={}",
            paymentId, request.orderId(), holdBeforeCommitMillis);

        try {
            jdbcTemplate.update(
                """
                    INSERT INTO payment (id, amount, order_id, payment_key, status)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                paymentId,
                request.amount(),
                request.orderId(),
                null,
                PaymentStatus.NOT_STARTED.name()
            );
        } catch (DataIntegrityViolationException exception) {
            log.warn("[Payment-Diagnostic] insert-only 중복 생성 orderId={}", request.orderId());
            throw new DuplicatePaymentException(request.orderId(), exception);
        }

        holdTransactionBeforeCommit(holdBeforeCommitMillis, paymentId, request.orderId());

        log.info("[Payment-Diagnostic] insert-only 결제 생성 완료 paymentId={}, orderId={}", paymentId, request.orderId());
        return new PaymentInsertDiagnosticResponse(
            paymentId,
            request.orderId(),
            PaymentStatus.NOT_STARTED,
            request.amount(),
            holdBeforeCommitMillis
        );
    }

    private long normalizeHoldBeforeCommitMillis(Long holdBeforeCommitMillis) {
        if (holdBeforeCommitMillis == null || holdBeforeCommitMillis < 0) {
            return 0L;
        }
        return holdBeforeCommitMillis;
    }

    private void holdTransactionBeforeCommit(long holdBeforeCommitMillis, long paymentId, Long orderId) {
        if (holdBeforeCommitMillis == 0L) {
            return;
        }

        try {
            log.info("[Payment-Diagnostic] 커밋 전 트랜잭션 대기 paymentId={}, orderId={}, holdBeforeCommitMillis={}",
                paymentId, orderId, holdBeforeCommitMillis);
            Thread.sleep(holdBeforeCommitMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[Payment-Diagnostic] 커밋 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
