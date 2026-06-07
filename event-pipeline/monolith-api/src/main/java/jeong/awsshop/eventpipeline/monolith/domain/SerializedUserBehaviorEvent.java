package jeong.awsshop.eventpipeline.monolith.domain;

/*
 * batch writer가 한 번만 직렬화한 이벤트다.
 *
 * eventId:
 * - ES document id를 만들 때 사용한다.
 *
 * json:
 * - HDFS JSONL 저장과 ES bulk document line에서 그대로 재사용한다.
 */
public record SerializedUserBehaviorEvent(
        Long eventId,
        String json
) {
}
