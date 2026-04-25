package jeong.awsshop.product.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product implements Persistable<Long> {

    @Id
    // snowflake ID
    private Long id;

    // 제품의 고유 식별자 역할을 하는 ASIN 필드
    @Column(name = "parent_asin", nullable = false, unique = true)
    private String parentAsin;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "main_category", nullable = false)
    private String mainCategory;

    @Column(name = "average_rating")
    private BigDecimal averageRating;

    @Column(name = "rating_number")
    private Integer ratingNumber;

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

    @Builder
    public Product(Long id, String parentAsin, String title, String mainCategory,
                   BigDecimal averageRating, Integer ratingNumber, BigDecimal price,
                   String store, String details) {
        this.id = id;
        this.parentAsin = parentAsin;
        this.title = title;
        this.mainCategory = mainCategory;
        this.averageRating = averageRating;
        this.ratingNumber = ratingNumber;
        this.price = price;
        this.store = store;
        this.details = details;
    }

    @Override
    public Long getId() {
        return id;
    }

    /* GeneratedValue 대신 Snowflake ID를 사용하므로, INSERT 가 가능하도록 true 반환 */
    @Override
    public boolean isNew() {
        return true;
    }

    public void addFeature(ProductFeature feature) {
        this.features.add(feature);
    }

    public void addDescription(ProductDescription description) {
        this.descriptions.add(description);
    }

    public void addCategory(ProductCategory category) {
        this.categories.add(category);
    }

    public void addBoughtTogether(ProductBoughtTogether boughtTogether) {
        this.boughtTogether.add(boughtTogether);
    }

    public void addImage(ProductImage image) {
        this.images.add(image);
    }

    public void addVideo(ProductVideo video) {
        this.videos.add(video);
    }
}
