package jeong.awsshop.stock.presentation;

import jeong.awsshop.stock.application.StockService;
import jeong.awsshop.stock.application.dto.StockResponse;
import jeong.awsshop.stock.presentation.dto.StockQuantityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    @PostMapping("{productId}/decrease")
    public StockResponse decrease(
        @PathVariable Long productId,
        @RequestBody StockQuantityRequest request
    ) {
        return stockService.decrease(productId, request.quantity());
    }

    @PostMapping("{productId}/increase")
    public StockResponse increase(
        @PathVariable Long productId,
        @RequestBody StockQuantityRequest request
    ) {
        return stockService.increase(productId, request.quantity());
    }
}
