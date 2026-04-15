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
 * 제품의 동영상 URL을 저장하는 엔티티
 *
 * 예시 :
 *      {
 *      "title": "Black Beaded Crystal Triple Protection Bracelets Jewelry 3",
 *      "url": "https://www.amazon.com/vdp/0d779107a06a42adb7f7915d16fa3052?ref=dp_vse_rvc_0",
 *      "user_id": ""
 *      },
 */
@Entity
@Table(name = "product_videos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String title;

    @Column
    private String url;

    // upload한 유저의 아이디 (현재는 대부분 null이거나 빈 문자열로 존재)
    private String userId;

    @Builder
    public ProductVideo(Product product, String url, String title, String userId) {
        this.product = product;
        this.url = url;
        this.title = title;
        this.userId = userId;
    }
}
