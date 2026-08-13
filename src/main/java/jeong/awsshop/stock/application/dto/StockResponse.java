package jeong.awsshop.stock.application.dto;

import jeong.awsshop.stock.domain.Stock;

public record StockResponse(
    Long productId,
    int quantity
) {

    public static StockResponse from(Stock stock) {
        return new StockResponse(stock.getProductId(), stock.getQuantity());
    }
}
