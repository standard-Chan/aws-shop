package jeong.awsshop.eventpipeline.monolith.domain;

/*
 * 외부 전달하는 데이터 포맷을 통일하기 위해 만든 DTO
 *
 * json:
 * - HDFS JSONL 저장과 ES bulk document line에서 그대로 재사용한다.
 */
public record SerializedUserBehaviorEvent(
        Long eventId,
        String json
) {
}
