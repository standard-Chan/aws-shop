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
 * 제품의 이미지 및 썸네일 URL을 저장하는 엔티티
 *
 * 예시 :
 *      {
 *         "thumb": "https://m.media-amazon.com/images/I/61FfqGgIMNL._AC_US40_.jpg",
 *         "large": "https://m.media-amazon.com/images/I/61FfqGgIMNL._AC_.jpg",
 *         "variant": "MAIN",
 *         "hi_res": "https://m.media-amazon.com/images/I/91Qthwjgl+L._AC_SL1500_.jpg"
 *       }
 */
@Entity
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // image의 타입 - MAIN, 기타(PT01, PT02...) 등
    private String variant;

    // 썸네일
    private String thumb;

    // 일반 이미지
    private String large;

    // 이미지 클릭 시 띄우는 고화질 이미지
    @Column(name = "hi_res")
    private String hiRes;

    @Builder
    public ProductImage(Product product, String variant, String thumb, String large, String hiRes) {
        this.product = product;
        this.variant = variant;
        this.thumb = thumb;
        this.large = large;
        this.hiRes = hiRes;
    }

    /**
     * 적재 흐름에서 사용할 child 생성 팩토리다.
     */
    public static ProductImage of(Product product, String variant, String thumb, String large, String hiRes) {
        return ProductImage.builder()
                .product(product)
                .variant(variant)
                .thumb(thumb)
                .large(large)
                .hiRes(hiRes)
                .build();
    }
}
