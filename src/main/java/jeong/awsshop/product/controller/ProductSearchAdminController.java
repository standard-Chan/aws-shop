package jeong.awsshop.product.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jeong.awsshop.product.service.search.ProductSearchReindexService;
import jeong.awsshop.product.service.search.dto.ProductSearchReindexResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products/search")
@RequiredArgsConstructor
@Validated
public class ProductSearchAdminController {

    private final ProductSearchReindexService productSearchReindexService;

    /**
     * MySQL 상품 데이터를 source of truth로 보고 ES 검색 read model을 page 단위로 다시 색인한다.
     * admin만 사용 가능한 API
     * TODO : API KEY를 받아서 관리자만 접근 가능하도록 인증/인가 처리 필요
     */
    @PostMapping("/reindex")
    public ProductSearchReindexResponse reindexProducts(
            @RequestParam(name = "pageSize", defaultValue = "500") @Min(1) @Max(5000) int pageSize
    ) {
        return productSearchReindexService.reindexAll(pageSize);
    }
}
