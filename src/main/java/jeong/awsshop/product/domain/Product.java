package jeong.awsshop.product.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    private Long id;

    // 제품의 고유 식별자 역할을 하는 ASIN 필드
    @Column(name = "parent_aisn", nullable = false, unique = true)
    private Long parentAisn;

    private String title;

    @Column(name = "main_category")
    private String mainCategory;

    @Column(name = "average_rating")
    private String averageRating;

    private BigDecimal price;

    private String store;

    /**
     * 타입 : JSON 형태로 저장되는 상세 정보 필드
     * 의도 : 제품마다, 필드 값이 다를 수 있는 상세 정보를 유연하게 저장하기 위해 JSON 사용
     * 예시: {"color": "red", "size": "M", "material": "cotton"}
     */
    @Column(columnDefinition = "json")
    private String details;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductFeature> features = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductDescription> descriptions = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductCategory> categories = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductBoughtTogether> boughtTogether = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVideo> videos = new ArrayList<>();
}
