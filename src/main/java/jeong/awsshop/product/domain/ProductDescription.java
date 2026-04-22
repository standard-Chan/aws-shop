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
 * 상품 상세 설명 엔티티
 * - 상품과 1:N 관계를 가지며, 각 상품은 여러 개의 상세 설명을 가질 수 있음
 * - descriptionIndex 필드를 통해 상세 설명의 순서를 관리할 수 있음
 *
 * 예시 : 아래 각 요소
 * [
 *     "Description",
 *     "The false toenails are durable with perfect length. You have the option to wear them long or clip them short, easy to trim and file them to in any length and shape you like. Plus, ABS is kind of green enviromental material, and makes the nails durable, breathable, light even no pressure on your own toenails. Fit well to your natural toenails. Non toxic, no smell, no harm to your health.",
 *     "Feature",
 *     "- Color: As Shown.- Material: ABS.- Size: 14.3 x 7.2 x 1cm.",
 *     "Package Including",
 *     "100 x Pieces fake toenails"
 *   ]
 */
@Entity
@Table(name = "product_descriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDescription {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column
    private Integer descriptionIndex;

    @Builder
    public ProductDescription(Long id, Product product, String description, Integer descriptionIndex) {
        this.id = id;
        this.product = product;
        this.description = description;
        this.descriptionIndex = descriptionIndex;
    }

    /**
     * 적재 흐름에서 사용할 child 생성 팩토리다.
     */
    public static ProductDescription of(Long id, Product product, String description, Integer descriptionIndex) {
        return ProductDescription.builder()
                .id(id)
                .product(product)
                .description(description)
                .descriptionIndex(descriptionIndex)
                .build();
    }
}

