package jeong.awsshop.review.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;


@Entity
@Table(
    name = "review",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_review_user_product_time",
            columnNames = {"user_id", "product_id", "timestamp"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String user;

    @Column(name = "product_id", nullable = false)
    private String parentAsin; // 상품 고유 ID

    @Column(name = "asin")
    private String asin; // 상품 ID (size, color 등에 따라 상이할 수 있다)

    @Column(name = "rating")
    private Float rating;

    @Column(length = 511)
    private String title;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "verified_purchase", nullable = false)
    private Boolean verifiedPurchase; // (구매 여부)

    @Column(name = "helpful_vote")
    private Integer helpfulVote; // 추천 수

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewImage> images = new ArrayList<>();

    @Builder
    public Review(Long reviewId, String user, String parentAsin, String asin,
        Float rating, String title, String text, Long timestamp,
        Boolean verifiedPurchase, Integer helpfulVote) {
        this.id = reviewId;
        this.user = user;
        this.parentAsin = parentAsin;
        this.asin = asin;
        this.rating = rating;
        this.title = title;
        this.text = text;
        this.timestamp = timestamp;
        this.verifiedPurchase = verifiedPurchase;
        this.helpfulVote = helpfulVote;
    }
}
