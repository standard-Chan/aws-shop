package jeong.awsshop.eventpipeline.productranking.domain;

import java.time.Duration;

public enum RankingWindow {
    ONE_HOUR(Duration.ofHours(1)),
    ONE_DAY(Duration.ofDays(1)),
    ONE_WEEK(Duration.ofDays(7));

    private final Duration duration;

    RankingWindow(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
