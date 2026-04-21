package jeong.awsshop.product.controller;

import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductReadService productReadService;

    /**
     * Product 목록을 cursor 방식으로 조회한다.
     */
    @GetMapping
    public ProductCursorResponse getProducts(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cursor
    ) {
        return productReadService.getProducts(size, cursor);
    }
}
