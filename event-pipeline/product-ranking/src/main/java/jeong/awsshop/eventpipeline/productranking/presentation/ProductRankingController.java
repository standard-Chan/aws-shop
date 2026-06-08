package jeong.awsshop.eventpipeline.productranking.presentation;

import jakarta.validation.Valid;
import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.productranking.application.ProductRankingService;
import jeong.awsshop.eventpipeline.productranking.presentation.dto.EventProcessingCountResponse;
import jeong.awsshop.eventpipeline.productranking.presentation.dto.ProductRankingMemoryStatsResponse;
import jeong.awsshop.eventpipeline.productranking.presentation.dto.ProductRankingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-pipeline/product-ranking")
public class ProductRankingController {

    private final ProductRankingService productRankingService;

    public ProductRankingController(ProductRankingService productRankingService) {
        this.productRankingService = productRankingService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void record(@Valid @RequestBody UserBehaviorEventMessage event) {
        productRankingService.record(event);
    }

    @GetMapping("/events/count")
    public EventProcessingCountResponse eventCount() {
        return new EventProcessingCountResponse(productRankingService.processedEventCount());
    }

    @GetMapping("/memory")
    public ProductRankingMemoryStatsResponse memoryStats() {
        return ProductRankingMemoryStatsResponse.from(productRankingService.memoryStats());
    }

    @GetMapping("/rankings")
    public List<ProductRankingResponse> rankings(@RequestParam(defaultValue = "10") int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return productRankingService.findTop(normalizedLimit).stream()
                .map(ProductRankingResponse::from)
                .toList();
    }
}
