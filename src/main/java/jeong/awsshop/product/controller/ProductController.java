package jeong.awsshop.product.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductReadService productReadService;

    /**
     * Product 목록을 cursor 방식으로 조회한다.
     */
    @GetMapping
    public ProductCursorResponse getProducts(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long cursor
    ) {
        return productReadService.getProducts(size, cursor);
    }

    /**
     * Category별 Product 목록을 cursor 방식으로 조회한다.
     */
    @GetMapping("/category")
    public ProductCategoryCursorResponse getProductsByCategory(
            @RequestParam String mainCategory,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        return productReadService.getProductsByCategory(
                mainCategory,
                size,
                cursorId,
                sort,
                direction
        );
    }

    /**
     * Product id로 단일 상품 상세 정보를 조회한다.
     */
    @GetMapping("/{id}")
    public ProductDetailResponse getProductDetail(@PathVariable @Positive Long id) {
        return productReadService.getProductDetail(id);
    }
}
