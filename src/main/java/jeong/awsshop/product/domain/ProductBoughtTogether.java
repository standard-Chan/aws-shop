package jeong.awsshop.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * 참고 : 99%의 데이터가 null로 존재
 */
@Entity
@Table(name = "product_bought_together")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductBoughtTogether {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Long relatedProductId;

    @Column
    private String relatedProductTitle;

    @Column
    private String relatedProductImageUrl;

    @Builder
    public ProductBoughtTogether(Long id, Product product, Long relatedProductId, String relatedProductTitle,
                                 String relatedProductImageUrl) {
        this.id = id;
        this.product = product;
        this.relatedProductId = relatedProductId;
        this.relatedProductTitle = relatedProductTitle;
        this.relatedProductImageUrl = relatedProductImageUrl;
    }

    /**
     * 적재 흐름에서 사용할 child 생성 팩토리다.
     */
    public static ProductBoughtTogether of(Long id, Product product, Long relatedProductId, String relatedProductTitle,
                                           String relatedProductImageUrl) {
        return ProductBoughtTogether.builder()
                .id(id)
                .product(product)
                .relatedProductId(relatedProductId)
                .relatedProductTitle(relatedProductTitle)
                .relatedProductImageUrl(relatedProductImageUrl)
                .build();
    }
}

