package jeong.awsshop.review.repository.projection;

public interface ReviewImageProjection {

    Long getReviewId();

    String getSmallImageUrl();

    String getMediumImageUrl();

    String getLargeImageUrl();

    String getAttachmentType();
}
