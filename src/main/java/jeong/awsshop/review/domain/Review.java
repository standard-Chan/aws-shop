package jeong.awsshop.review.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = true)
    private String user;

    @Column(name = "product_id")
    private String parentAsin;

    @Column(name = "asin")
    private String asin;

    @Column(name = "rating", nullable = false)
    private Float rating;

    @Column(length = 511)
    private String title;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "verified_purchase", nullable = false)
    private Boolean verifiedPurchase;

    @Column(name = "helpful_vote", nullable = false)
    private Integer helpfulVote;

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
