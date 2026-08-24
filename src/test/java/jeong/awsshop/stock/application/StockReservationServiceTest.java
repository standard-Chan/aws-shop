package jeong.awsshop.stock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.stock.domain.Stock;
import jeong.awsshop.stock.domain.StockRepository;
import jeong.awsshop.stock.domain.StockReservation;
import jeong.awsshop.stock.domain.StockReservationRepository;
import jeong.awsshop.stock.domain.StockReservationStatus;
import jeong.awsshop.stock.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class StockReservationServiceTest {

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        stockReservationRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("일부 상품 재고가 부족하면 전체 예약과 선차감 재고를 롤백해야 한다")
    void should_rollback_all_reserved_stock_when_later_reservation_fails() {
        Product first = saveProductWithStock(nextProductId(), 10);
        Product second = saveProductWithStock(nextProductId(), 1);

        assertThatThrownBy(() -> stockReservationService.reserve(
            2L,
            123L,
            List.of(
                orderLine(first.getId(), 3),
                orderLine(second.getId(), 2)
            )
        )).isInstanceOf(InsufficientStockException.class);

        assertThat(stockRepository.findByProductId(first.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(stockRepository.findByProductId(second.getId()).orElseThrow().getQuantity()).isEqualTo(1);
        assertThat(stockReservationRepository.findAllByPaymentId(2L)).isEmpty();
    }

    @Test
    @DisplayName("예약 복구는 RESERVED row만 재고를 되돌리고 중복 호출되어도 한 번만 반영해야 한다")
    void should_restore_reserved_stock_idempotently() {
        Product product = saveProductWithStock(nextProductId(), 10);
        stockReservationService.reserve(3L, 123L, List.of(orderLine(product.getId(), 4)));

        stockReservationService.restore(3L);
        stockReservationService.restore(3L);

        StockReservation reservation = stockReservationRepository.findAllByPaymentId(3L).get(0);
        assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.RESTORED);
        assertThat(reservation.getRestoredAt()).isNotNull();
    }

    @Test
    @DisplayName("예약 완료는 RESERVED row만 완료 처리하고 중복 호출되어도 상태를 유지해야 한다")
    void should_complete_reserved_stock_idempotently() {
        Product product = saveProductWithStock(nextProductId(), 10);
        stockReservationService.reserve(4L, 123L, List.of(orderLine(product.getId(), 4)));

        stockReservationService.complete(4L);
        stockReservationService.complete(4L);

        StockReservation reservation = stockReservationRepository.findAllByPaymentId(4L).get(0);
        assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(6);
        assertThat(reservation.getStatus()).isEqualTo(StockReservationStatus.COMPLETED);
        assertThat(reservation.getCompletedAt()).isNotNull();
    }

    private Product saveProductWithStock(Long productId, int quantity) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Product product = productRepository.save(product(productId));
            stockRepository.save(Stock.create(product, quantity));
            return product;
        });
    }

    private Product product(Long id) {
        return Product.builder()
            .id(id)
            .parentAsin("STOCK-RESERVATION-TEST-" + id)
            .title("재고 예약 테스트 상품 " + id)
            .mainCategory("테스트")
            .build();
    }

    private Long nextProductId() {
        return System.nanoTime();
    }

    private OrderLineSummary orderLine(Long productId, int quantity) {
        BigDecimal unitPrice = new BigDecimal("10.00");
        return new OrderLineSummary(
            productId,
            quantity,
            unitPrice,
            unitPrice.multiply(BigDecimal.valueOf(quantity))
        );
    }
}
