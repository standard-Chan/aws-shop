package jeong.awsshop.stock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.stock.application.dto.StockResponse;
import jeong.awsshop.stock.domain.Stock;
import jeong.awsshop.stock.domain.StockRepository;
import jeong.awsshop.stock.exception.InsufficientStockException;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import jeong.awsshop.stock.exception.StockNotFoundException;
import jeong.awsshop.stock.exception.StockProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ProductRepository productRepository;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(stockRepository, productRepository);
    }

    @Test
    @DisplayName("재고 차감 조건부 update가 성공하면 변경 후 재고를 반환해야 한다")
    void should_return_stock_after_decrease_when_update_succeeds() {
        when(stockRepository.decreaseIfEnough(1L, 3)).thenReturn(1);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock(1L, 7)));

        StockResponse response = stockService.decrease(1L, 3);

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.quantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("차감 대상 재고가 없으면 예외를 던져야 한다")
    void should_throw_exception_when_stock_does_not_exist_on_decrease() {
        when(stockRepository.decreaseIfEnough(1L, 3)).thenReturn(0);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.decrease(1L, 3))
            .isInstanceOf(StockNotFoundException.class)
            .hasMessage("[Stock] 재고가 존재하지 않습니다. productId=1");
    }

    @Test
    @DisplayName("차감 수량보다 현재 재고가 부족하면 예외를 던져야 한다")
    void should_throw_exception_when_stock_is_insufficient() {
        when(stockRepository.decreaseIfEnough(1L, 5)).thenReturn(0);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock(1L, 2)));

        assertThatThrownBy(() -> stockService.decrease(1L, 5))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessage("[Stock] 재고가 부족합니다. productId=1, requestedQuantity=5, currentQuantity=2");
    }

    @Test
    @DisplayName("기존 재고가 있으면 추가 수량만큼 증가한 재고를 반환해야 한다")
    void should_increase_existing_stock() {
        Stock stock = stock(1L, 10);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        StockResponse response = stockService.increase(1L, 4);

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.quantity()).isEqualTo(14);
        verify(productRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("재고가 없고 상품이 존재하면 새 재고를 생성해야 한다")
    void should_create_stock_when_stock_does_not_exist_and_product_exists() {
        Product product = product(1L);
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockResponse response = stockService.increase(1L, 8);

        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getProduct()).isEqualTo(product);
        assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(8);
        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.quantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("재고가 없고 상품도 없으면 예외를 던져야 한다")
    void should_throw_exception_when_product_does_not_exist_on_increase() {
        when(stockRepository.findByProductId(1L)).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.increase(1L, 8))
            .isInstanceOf(StockProductNotFoundException.class)
            .hasMessage("[Stock] 재고를 추가할 상품이 존재하지 않습니다. productId=1");
    }

    @Test
    @DisplayName("재고 변경 수량이 0 이하이면 repository를 호출하지 않고 예외를 던져야 한다")
    void should_throw_exception_when_quantity_is_not_positive() {
        assertThatThrownBy(() -> stockService.decrease(1L, 0))
            .isInstanceOf(InvalidStockQuantityException.class)
            .hasMessage("[Stock] 재고 변경 수량은 양수여야 합니다. quantity=0");

        assertThatThrownBy(() -> stockService.increase(1L, -1))
            .isInstanceOf(InvalidStockQuantityException.class)
            .hasMessage("[Stock] 재고 변경 수량은 양수여야 합니다. quantity=-1");

        verify(stockRepository, never()).decreaseIfEnough(1L, 0);
        verify(stockRepository, never()).findByProductId(1L);
    }

    private Stock stock(Long productId, int quantity) {
        return Stock.create(product(productId), quantity);
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
