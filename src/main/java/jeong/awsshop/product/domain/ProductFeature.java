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
 * 제품의 주요 특징을 나타내는 엔티티입니다.
 * 각 제품은 여러 개의 특징을 가질 수 있으며, 특징은 제품과 연관되어 저장됩니다.
 * 특징은 제품의 주요 속성이나 기능을 설명하는 텍스트로 구성됩니다.
 *
 * 의도
 *  ElasticSearch에서 제품 특징을 중심으로 검색 시, 사용할 예정입니다.
 *  따라서 별도 Entity로 분리하여 제품과 1:N 관계로 구성하였습니다.
 *
 *  예시 : [
 *     "The false toenails are durable with perfect length. You have the option to wear them long or clip them short, easy to trim and file them to in any length and shape you like.",
 *     "ABS is kind of green enviromental material, and makes the nails durable, breathable, light even no pressure on your own nails.",
 *     "Fit well to your natural toenails. Non toxic, no smell, no harm to your health.",
 *     "Wonderful as gift for girlfriend, family and friends.",
 *     "The easiest and most efficient way to do your toenail tips for manicures or nail art designs. It's fashion, creative, a useful accessory brighten up your look, also as a gift."
 *   ]
 *
 */
@Entity
@Table(name = "product_features")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String feature;

    @Column
    private Integer featureIndex;

    @Builder
    public ProductFeature(Product product, String feature, Integer featureIndex) {
        this.product = product;
        this.feature = feature;
        this.featureIndex = featureIndex;
    }
}

