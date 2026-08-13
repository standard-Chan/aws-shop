package jeong.awsshop.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.stock.domain.Stock;
import jeong.awsshop.stock.domain.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockRepositoryTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        stockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("재고가 충분하면 조건부 update로 재고를 차감해야 한다")
    void should_decrease_stock_when_quantity_is_enough() {
        Long productId = nextProductId();
        Product product = productRepository.save(product(productId));
        stockRepository.save(Stock.create(product, 10));

        int updatedCount = stockRepository.decreaseIfEnough(productId, 3);

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertThat(updatedCount).isEqualTo(1);
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고가 부족하면 조건부 update를 하지 않고 수량을 유지해야 한다")
    void should_not_decrease_stock_when_quantity_is_insufficient() {
        Long productId = nextProductId();
        Product product = productRepository.save(product(productId));
        stockRepository.save(Stock.create(product, 2));

        int updatedCount = stockRepository.decreaseIfEnough(productId, 3);

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertThat(updatedCount).isEqualTo(0);
        assertThat(stock.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품 id가 Stock의 PK로 저장되어야 한다")
    void should_use_product_id_as_stock_id() {
        Long productId = nextProductId();
        Product product = productRepository.save(product(productId));

        Stock stock = stockRepository.save(Stock.create(product, 5));

        assertThat(stock.getProductId()).isEqualTo(productId);
        assertThat(stockRepository.findById(productId)).isPresent();
    }

    private Long nextProductId() {
        return System.nanoTime();
    }

    private Product product(Long id) {
        return Product.builder()
            .id(id)
            .parentAsin("STOCK-TEST-" + id)
            .title("재고 테스트 상품 " + id)
            .mainCategory("테스트")
            .build();
    }
}
