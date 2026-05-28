package jeong.awsshop.payment.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import jeong.awsshop.payment.domain.Payment;
import jeong.awsshop.payment.exception.DuplicatePaymentException;
import jeong.awsshop.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCreateWriter {

    private static final int ORDER_LOCK_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;

    public void insert(Payment payment) {
        String lockName = createOrderLockName(payment.getOrderId());

        try (Connection connection = dataSource.getConnection()) {
            // 네임드 락 획득
            acquireOrderLock(connection, lockName, payment.getOrderId());

            try {
                // INSERT 실행
                insertPayment(connection, payment);
            } finally {
                releaseOrderLock(connection, lockName, payment.getOrderId());
            }
        } catch (SQLException exception) {
            throw new PaymentException("[Payment] 결제 생성 중 DB 오류가 발생했습니다. orderId=" + payment.getOrderId(),
                exception);
        }
    }

    private void acquireOrderLock(Connection connection, String lockName, Long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, ORDER_LOCK_TIMEOUT_SECONDS);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new PaymentException("[Payment] 결제 생성 락 획득에 실패했습니다. orderId=" + orderId);
                }
            }
        }
    }

    private void insertPayment(Connection connection, Payment payment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
                INSERT INTO payment (id, amount, order_id, payment_key, status)
                VALUES (?, ?, ?, ?, ?)
                """
        )) {
            statement.setLong(1, payment.getId());
            statement.setBigDecimal(2, payment.getAmount());
            statement.setLong(3, payment.getOrderId());
            statement.setString(4, payment.getPaymentKey());
            statement.setString(5, payment.getStatus().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1062) {
                log.warn("[Payment] 결제 Entity 중복 생성 orderId={}", payment.getOrderId());
                throw new DuplicatePaymentException(payment.getOrderId(), exception);
            }
            throw exception;
        }
    }

    private void releaseOrderLock(Connection connection, String lockName, Long orderId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery();
        } catch (SQLException exception) {
            log.error("[Payment] 결제 생성 락 해제에 실패했습니다. orderId={}, lockName={}", orderId, lockName, exception);
        }
    }

    private String createOrderLockName(Long orderId) {
        return "payment:create:" + orderId;
    }
}
