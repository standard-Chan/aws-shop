package jeong.awsshop.product.service.productread;

import java.util.List;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductReadService {

    private final ProductRepository productRepository;

    /**
     * cursor 목록 조회 결과를 응답 DTO로 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductCursorResponse getProducts(int size, Long cursor) {
        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(cursor, queryLimitForHasNext(size));
        return ProductCursorResponse.from(rows, size);
    }

    private int queryLimitForHasNext(int size) {
        return size + 1;
    }
}
