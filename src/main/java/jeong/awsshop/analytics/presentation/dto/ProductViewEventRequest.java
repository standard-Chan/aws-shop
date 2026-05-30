package jeong.awsshop.analytics.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductViewEventRequest(
        @NotNull
        @Positive Long userId,
        @NotNull
        @Positive Long productId,
        @Positive Long searchEventId,
        String searchKeyword
) {

    /**
     * 선택 필드라 null은 허용하지만, 값이 있으면 분석 차원으로 쓸 수 있게 blank를 막는다.
     */
    @AssertTrue
    public boolean isSearchKeywordValid() {
        return searchKeyword == null || !searchKeyword.isBlank();
    }
}
