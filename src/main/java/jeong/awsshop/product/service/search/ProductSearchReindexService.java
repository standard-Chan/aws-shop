package jeong.awsshop.product.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import jeong.awsshop.product.config.ProductSearchElasticsearchProperties;
import jeong.awsshop.product.exception.search.ProductSearchIndexPreparationException;
import jeong.awsshop.product.exception.search.ProductSearchReindexException;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.search.document.ProductSearchDocument;
import jeong.awsshop.product.service.search.dto.ProductSearchReindexResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSearchReindexService {

    private final ProductRepository productRepository;
    private final ElasticsearchClient elasticsearchClient;
    private final ProductSearchElasticsearchProperties properties;

    @Transactional(readOnly = true)
    public ProductSearchReindexResponse reindexAll(int pageSize) {
        Instant startedAt = Instant.now();
        ensureIndex();

        long indexedCount = 0;
        long failedCount = 0;
        Long cursorId = null;

        while (true) {
            List<ProductSummaryNativeProjection> rows =
                    productRepository.findProductSearchReindexPage(cursorId, pageSize);
            if (rows.isEmpty()) {
                break;
            }

            BulkResponse bulkResponse = bulkIndex(rows);
            indexedCount += rows.size();
            if (bulkResponse.errors()) {
                failedCount += bulkResponse.items().stream()
                        .filter(item -> item.error() != null)
                        .count();
            }
            cursorId = rows.get(rows.size() - 1).getId();
        }

        return new ProductSearchReindexResponse(
                indexedCount,
                failedCount,
                Duration.between(startedAt, Instant.now()).toMillis()
        );
    }

    private void ensureIndex() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(e -> e.index(properties.indexName()))
                    .value();
            if (exists) {
                return;
            }
            elasticsearchClient.indices().create(c -> c
                    .index(properties.indexName())
                    .mappings(m -> m
                            .properties("id", p -> p.long_(v -> v))
                            .properties("parentAsin", p -> p.keyword(v -> v))
                            .properties("title", p -> p.text(v -> v))
                            .properties("mainCategory", p -> p.keyword(v -> v))
                            .properties("averageRating", p -> p.double_(v -> v))
                            .properties("ratingNumber", p -> p.integer(v -> v))
                            .properties("price", p -> p.double_(v -> v))
                            .properties("store", p -> p.keyword(v -> v))
                    )
            );
        } catch (IOException e) {
            throw new ProductSearchIndexPreparationException(e);
        }
    }

    private BulkResponse bulkIndex(List<ProductSummaryNativeProjection> rows) {
        try {
            return elasticsearchClient.bulk(b -> {
                rows.stream()
                        .map(ProductSearchDocument::from)
                        .forEach(document -> b.operations(op -> op.index(index -> index
                                .index(properties.indexName())
                                .id(String.valueOf(document.id()))
                                .document(document)
                        )));
                return b;
            });
        } catch (IOException e) {
            throw new ProductSearchReindexException(e);
        }
    }
}
