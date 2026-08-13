package jeong.awsshop.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jeong.awsshop.product.domain.Product;
import jeong.awsshop.stock.exception.InsufficientStockException;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import jeong.awsshop.stock.exception.StockQuantityOverflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockTest {

    @Test
    @DisplayName("재고를 생성하면 상품 id와 초기 수량을 가진다")
    void should_create_stock_with_product_id_and_initial_quantity() {
        Product product = product(1L);

        Stock stock = Stock.create(product, 10);

        assertThat(stock.getProductId()).isEqualTo(1L);
        assertThat(stock.getProduct()).isEqualTo(product);
        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고를 차감하면 수량이 감소해야 한다")
    void should_decrease_quantity() {
        Stock stock = stock(1L, 10);

        stock.decrease(3);

        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고를 추가하면 수량이 증가해야 한다")
    void should_increase_quantity() {
        Stock stock = stock(1L, 10);

        stock.increase(5);

        assertThat(stock.getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("재고보다 큰 수량을 차감하면 예외를 던져야 한다")
    void should_throw_exception_when_decrease_quantity_is_greater_than_stock() {
        Stock stock = stock(1L, 2);

        assertThatThrownBy(() -> stock.decrease(3))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessage("[Stock] 재고가 부족합니다. productId=1, requestedQuantity=3, currentQuantity=2");
    }

    @Test
    @DisplayName("재고 변경 수량이 0 이하이면 예외를 던져야 한다")
    void should_throw_exception_when_quantity_is_not_positive() {
        Stock stock = stock(1L, 10);

        assertThatThrownBy(() -> stock.decrease(0))
            .isInstanceOf(InvalidStockQuantityException.class)
            .hasMessage("[Stock] 재고 변경 수량은 양수여야 합니다. quantity=0");

        assertThatThrownBy(() -> stock.increase(-1))
            .isInstanceOf(InvalidStockQuantityException.class)
            .hasMessage("[Stock] 재고 변경 수량은 양수여야 합니다. quantity=-1");
    }

    @Test
    @DisplayName("재고 추가 후 int 범위를 넘으면 예외를 던져야 한다")
    void should_throw_exception_when_quantity_overflows() {
        Stock stock = stock(1L, Integer.MAX_VALUE);

        assertThatThrownBy(() -> stock.increase(1))
            .isInstanceOf(StockQuantityOverflowException.class)
            .hasMessage("[Stock] 재고 수량이 허용 범위를 초과합니다. productId=1, currentQuantity=2147483647, addedQuantity=1");
    }

    private Stock stock(Long productId, int quantity) {
        Product product = product(productId);
        return Stock.create(product, quantity);
    }

    private Product product(Long id) {
        return Product.builder()
            .id(id)
            .parentAsin("ASIN-" + id)
            .title("상품 " + id)
            .mainCategory("테스트")
            .build();
    }
}
