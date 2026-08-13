package jeong.awsshop.stock.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE stock
        SET quantity = quantity - :quantity
        WHERE product_id = :productId
          AND quantity >= :quantity
        """, nativeQuery = true)
    int decreaseIfEnough(@Param("productId") Long productId, @Param("quantity") int quantity);
}
