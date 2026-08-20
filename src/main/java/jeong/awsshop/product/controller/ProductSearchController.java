package jeong.awsshop.product.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jeong.awsshop.product.service.search.ProductSearchService;
import jeong.awsshop.product.service.search.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    /**
     * Elasticsearch 를 사용해 keyword 기반 상품 검색을 수행한다.
     */
    @GetMapping("/search")
    public ProductSearchResponse searchProducts(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "cursor", required = false) String cursor
    ) {
        return productSearchService.search(keyword, size, sort, order, cursor);
    }
}
