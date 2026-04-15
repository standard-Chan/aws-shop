package jeong.awsshop.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    public ProductBoughtTogether(Product product, Long relatedProductId, String relatedProductTitle, String relatedProductImageUrl) {
        this.product = product;
        this.relatedProductId = relatedProductId;
        this.relatedProductTitle = relatedProductTitle;
        this.relatedProductImageUrl = relatedProductImageUrl;
    }
}

