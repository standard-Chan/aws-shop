package jeong.awsshop.product.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.product.config.ProductSearchElasticsearchProperties;
import jeong.awsshop.product.service.search.criteria.ProductSearchDirection;
import jeong.awsshop.product.service.search.criteria.ProductSearchSort;
import jeong.awsshop.product.service.search.cursor.ProductSearchCursor;
import jeong.awsshop.product.service.search.cursor.ProductSearchCursorCodec;
import jeong.awsshop.product.service.search.document.ProductSearchDocument;
import jeong.awsshop.product.service.search.dto.ProductSearchItemResponse;
import jeong.awsshop.product.service.search.dto.ProductSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final ProductSearchElasticsearchProperties properties;
    private final ProductSearchCursorCodec cursorCodec;

    public ProductSearchResponse search(
            String keyword,
            int size,
            String sort,
            String order,
            String cursorToken
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isBlank()) {
            return ProductSearchResponse.empty();
        }

        ProductSearchSort selectedSort = ProductSearchSort.from(sort);
        ProductSearchDirection selectedDirection = ProductSearchDirection.from(order);
        ProductSearchCursor cursor = cursorCodec.decode(cursorToken, selectedSort, selectedDirection);

        try {
            SearchResponse<ProductSearchDocument> response = elasticsearchClient.search(
                    buildSearchRequest(normalizedKeyword, size, selectedSort, selectedDirection, cursor),
                    ProductSearchDocument.class
            );
            return toResponse(response.hits().hits(), size, selectedSort, selectedDirection);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product Elasticsearch search failed", e);
        }
    }

    private SearchRequest buildSearchRequest(
            String keyword,
            int size,
            ProductSearchSort sort,
            ProductSearchDirection direction,
            ProductSearchCursor cursor
    ) {
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(properties.indexName())
                // 다음 페이지 판정을 위해 요청 크기보다 1개 더 조회한다.
                .size(size + 1)
                .query(q -> q.bool(b -> b
                        .must(m -> m.match(mm -> mm.field("title").query(keyword)))
                        .filter(f -> f.exists(e -> e.field(sort.fieldName())))
                ))
                .sort(s -> s.field(f -> f.field(sort.fieldName()).order(toSortOrder(direction))))
                .sort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)))
                .highlight(h -> h.fields("title", f -> f.preTags("<em>").postTags("</em>")));

        List<FieldValue> searchAfter = searchAfterValues(cursor, sort);
        if (!searchAfter.isEmpty()) {
            builder.searchAfter(searchAfter);
        }
        return builder.build();
    }

    private ProductSearchResponse toResponse(
            List<Hit<ProductSearchDocument>> hits,
            int size,
            ProductSearchSort sort,
            ProductSearchDirection direction
    ) {
        boolean hasNext = hits.size() > size;
        List<Hit<ProductSearchDocument>> pageHits = hasNext ? hits.subList(0, size) : hits;
        List<ProductSearchItemResponse> products = pageHits.stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> ProductSearchItemResponse.from(hit.source(), hit.score(), hit.highlight()))
                .toList();

        String nextCursor = null;
        if (hasNext && !pageHits.isEmpty()) {
            ProductSearchDocument lastDocument = pageHits.get(pageHits.size() - 1).source();
            if (lastDocument != null) {
                nextCursor = cursorCodec.encode(sort, direction, lastDocument);
            }
        }
        return new ProductSearchResponse(products, nextCursor, hasNext);
    }

    private List<FieldValue> searchAfterValues(ProductSearchCursor cursor, ProductSearchSort sort) {
        if (cursor == null) {
            return List.of();
        }
        try {
            List<FieldValue> values = new ArrayList<>();
            values.add(sortFieldValue(cursor.sortValue(), sort));
            values.add(FieldValue.of(cursor.id()));
            return values;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product search cursor", e);
        }
    }

    private FieldValue sortFieldValue(String sortValue, ProductSearchSort sort) {
        if (sort == ProductSearchSort.RATING_NUMBER) {
            return FieldValue.of(Long.parseLong(sortValue));
        }
        return FieldValue.of(new BigDecimal(sortValue).doubleValue());
    }

    private SortOrder toSortOrder(ProductSearchDirection direction) {
        if (direction == ProductSearchDirection.ASC) {
            return SortOrder.Asc;
        }
        return SortOrder.Desc;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim();
    }
}
