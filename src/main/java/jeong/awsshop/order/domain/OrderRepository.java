package jeong.awsshop.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE orders
        SET status = 'EXECUTING'
        WHERE id = :id
            AND status <> 'COMPLETED'
            AND status <> 'CANCELED'
            AND status <> 'EXPIRED'
            AND status <> 'EXECUTING'
        """, nativeQuery = true)
    int updateStatusToExecutingIfAvailable(@Param("id") Long id);
}
