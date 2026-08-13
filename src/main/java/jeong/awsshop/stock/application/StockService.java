package jeong.awsshop.stock.application;

import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.stock.application.dto.StockResponse;
import jeong.awsshop.stock.domain.Stock;
import jeong.awsshop.stock.domain.StockRepository;
import jeong.awsshop.stock.exception.InsufficientStockException;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import jeong.awsshop.stock.exception.StockNotFoundException;
import jeong.awsshop.stock.exception.StockProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;

    @Transactional
    public StockResponse decrease(Long productId, int quantity) {
        validateQuantity(quantity);

        int updatedCount = stockRepository.decreaseIfEnough(productId, quantity);
        if (updatedCount == 1) {
            return StockResponse.from(getStock(productId));
        }

        Stock stock = stockRepository.findByProductId(productId)
            .orElseThrow(() -> new StockNotFoundException(productId));
        throw new InsufficientStockException(productId, quantity, stock.getQuantity());
    }

    @Transactional
    public StockResponse increase(Long productId, int quantity) {
        validateQuantity(quantity);

        return stockRepository.findByProductId(productId)
            .map(stock -> {
                stock.increase(quantity);
                return StockResponse.from(stock);
            })
            .orElseGet(() -> StockResponse.from(createStock(productId, quantity)));
    }

    private Stock createStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new StockProductNotFoundException(productId));
        return stockRepository.save(Stock.create(product, quantity));
    }

    private Stock getStock(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new StockNotFoundException(productId));
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidStockQuantityException(quantity);
        }
    }
}
