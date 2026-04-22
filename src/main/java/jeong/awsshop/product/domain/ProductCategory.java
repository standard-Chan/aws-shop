package jeong.awsshop.product.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * 제품의 세부 카테고리를 나타내는 엔티티
 * 상품 판매자가 제품을 등록할 때, 세부 카테고리를 저장하기 위해 사용
 * 의도 : elasticsearch에서 제품 검색 시, 세부 카테고리를 활용하기 위해 별도 엔티티로 분리
 * 주의 : 비어있는 배열이거나, null인 경우도 존재할 수 있다.
 *
 * 예시 : 아래 리스트의 하나의 요소
 * [
 *   "Handmade Products",
 *   "Clothing, Shoes & Accessories",
 *   "Luggage & Travel Gear",
 *   "Key & Identification Accessories",
 *   "Keychains & Keyrings"
 * ]
 */
@Entity
@Table(name = "product_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategory {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String category;

    @Builder
    public ProductCategory(Long id, Product product, String category) {
        this.id = id;
        this.product = product;
        this.category = category;
    }

    /**
     * 적재 흐름에서 사용할 child 생성 팩토리다.
     */
    public static ProductCategory of(Long id, Product product, String category) {
        return ProductCategory.builder()
                .id(id)
                .product(product)
                .category(category)
                .build();
    }
}

