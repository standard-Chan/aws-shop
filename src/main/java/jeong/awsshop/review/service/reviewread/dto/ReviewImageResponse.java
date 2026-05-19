package jeong.awsshop.review.service.reviewread.dto;

import jeong.awsshop.review.repository.projection.ReviewImageProjection;

public record ReviewImageResponse(
        String smallImageUrl,
        String mediumImageUrl,
        String largeImageUrl,
        String attachmentType
) {

    /**
     * image projection을 응답 DTO로 변환한다.
     */
    public static ReviewImageResponse from(ReviewImageProjection row) {
        return new ReviewImageResponse(
                row.getSmallImageUrl(),
                row.getMediumImageUrl(),
                row.getLargeImageUrl(),
                row.getAttachmentType()
        );
    }
}
