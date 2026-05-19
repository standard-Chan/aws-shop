package jeong.awsshop.review.repository.projection;

public interface ReviewSummaryProjection {

    Long getId();

    Float getRating();

    String getTitle();

    String getText();

    Long getTimestamp();

    String getUserId();

    Boolean getVerifiedPurchase();

    Integer getHelpfulVote();

    String getAsin();

    String getParentAsin();
}
