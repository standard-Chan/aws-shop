package jeong.awsshop.product.service.productread;

import java.util.List;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductImageResponse;
import jeong.awsshop.product.service.productread.dto.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductReadService {

    private final ProductRepository productRepository;

    /**
     * cursor 목록 조회 결과를 응답 DTO로 조립한다.
     */
    @Transactional(readOnly = true)
    public ProductCursorResponse getProducts(int size, Long cursor) {
        validateSize(size);

        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(cursor, size + 1);

        boolean hasNext = rows.size() > size;
        List<ProductSummaryResponse> products = rows.stream()
                .limit(size)
                .map(this::toResponse)
                .toList();

        Long nextCursorId = products.isEmpty() ? null : products.getLast().id();

        return new ProductCursorResponse(products, nextCursorId, hasNext);
    }

    private void validateSize(int size) {
        if (size <= 0 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "[상품 목록 조회 실패]: size는 1 이상 100 이하여야 합니다."
            );
        }
    }

    private ProductSummaryResponse toResponse(ProductSummaryNativeProjection row) {
        return new ProductSummaryResponse(
                row.getId(),
                row.getParentAsin(),
                row.getTitle(),
                MainCategory.valueOf(row.getMainCategory()),
                row.getAverageRating(),
                row.getRatingNumber(),
                row.getPrice(),
                row.getStore(),
                toImageResponse(row)
        );
    }

    private ProductImageResponse toImageResponse(ProductSummaryNativeProjection row) {
        if (row.getImageVariant() == null
                && row.getImageThumb() == null
                && row.getImageLarge() == null
                && row.getImageHiRes() == null) {
            return null;
        }

        return new ProductImageResponse(
                row.getImageVariant(),
                row.getImageThumb(),
                row.getImageLarge(),
                row.getImageHiRes()
        );
    }
}
