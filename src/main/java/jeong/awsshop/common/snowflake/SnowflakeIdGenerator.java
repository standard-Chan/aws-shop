package jeong.awsshop.common.snowflake;

import org.springframework.stereotype.Component;

/**
 * Twitter Snowflake 알고리즘 기반 분산 고유 ID 생성기
 *
 * 사용 의도 : index에 bulk insert를 할때, 저장 효율을 높이기 위해서 고유 ID를 생성하는데 사용
 *
 * 64-bit ID 구조:
 * +-----------+------------------+----------+-----------+
 * | 0 (1bit)  | timestamp (41bit)| node(10) | seq (12)  |
 * +-----------+------------------+----------+-----------+
 */
@Component
public class SnowflakeIdGenerator {

    // ---- 비트 할당 ----
    private static final long EPOCH             = 1_700_000_000_000L; // 기준 시각 (ms) — 2023-11-14 고정

    private static final long NODE_ID_BITS      = 10L;
    private static final long SEQUENCE_BITS     = 12L;

    private static final long MAX_NODE_ID       = ~(-1L << NODE_ID_BITS);   // 1023
    private static final long MAX_SEQUENCE      = ~(-1L << SEQUENCE_BITS);  // 4095

    private static final long NODE_ID_SHIFT     = SEQUENCE_BITS;                      // 12
    private static final long TIMESTAMP_SHIFT   = SEQUENCE_BITS + NODE_ID_BITS;       // 22

    // ---- 상태 ----
    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence      = 0L;

    // -----------------------------------------------------------------------
    // 생성자
    // -----------------------------------------------------------------------

    public SnowflakeIdGenerator() {
        this(1L); // 기본 nodeId = 0
    }

    /**
     * @param nodeId 0 ~ 1023 범위의 노드(머신) 식별자
     */
    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                "nodeId must be between 0 and " + MAX_NODE_ID + ", got: " + nodeId);
        }
        this.nodeId = nodeId;
    }

    // -----------------------------------------------------------------------
    // ID 생성 (thread-safe)
    // -----------------------------------------------------------------------

    /**
     * 고유한 Snowflake ID를 반환합니다.
     */
    public synchronized long nextId() {
        long now = currentTimeMs();

        // 시계 역행 감지
        if (now < lastTimestamp) {
            throw new IllegalStateException(
                "Clock moved backwards. Refusing to generate id for "
                    + (lastTimestamp - now) + " ms");
        }

        if (now == lastTimestamp) {
            // 같은 밀리초 내 → sequence 증가
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // sequence 소진 → 다음 밀리초까지 대기
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            // 새로운 밀리초 → sequence 리셋
            sequence = 0L;
        }

        lastTimestamp = now;

        return ((now - EPOCH) << TIMESTAMP_SHIFT)
            | (nodeId          << NODE_ID_SHIFT)
            | sequence;
    }

    // -----------------------------------------------------------------------
    // 파싱 유틸리티
    // -----------------------------------------------------------------------

    /**
     * Snowflake ID를 사람이 읽기 쉬운 구성 요소로 분해합니다.
     */
    public static ParsedId parse(long id) {
        long ts       = (id >> TIMESTAMP_SHIFT) + EPOCH;
        long node     = (id >> NODE_ID_SHIFT) & MAX_NODE_ID;
        long seq      = id & MAX_SEQUENCE;
        return new ParsedId(ts, node, seq);
    }

    /** 생성 시각(ms)만 빠르게 추출합니다. */
    public static long extractTimestampMs(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------

    private long currentTimeMs() {
        return System.currentTimeMillis();
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTimeMs();
        while (ts <= lastTs) {
            ts = currentTimeMs();
        }
        return ts;
    }

    // -----------------------------------------------------------------------
    // 파싱 결과 레코드
    // -----------------------------------------------------------------------

    public static final class ParsedId {
        public final long timestampMs;
        public final long nodeId;
        public final long sequence;

        ParsedId(long timestampMs, long nodeId, long sequence) {
            this.timestampMs = timestampMs;
            this.nodeId      = nodeId;
            this.sequence    = sequence;
        }

        @Override
        public String toString() {
            return String.format(
                "ParsedId{timestamp=%d (%s), nodeId=%d, sequence=%d}",
                timestampMs,
                java.time.Instant.ofEpochMilli(timestampMs),
                nodeId,
                sequence);
        }
    }

    // -----------------------------------------------------------------------
    // 사용 예시
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1); // nodeId = 1

        // ID 생성
        long id1 = gen.nextId();
        long id2 = gen.nextId();
        long id3 = gen.nextId();

        System.out.println("Generated IDs:");
        System.out.println("  id1 = " + id1);
        System.out.println("  id2 = " + id2);
        System.out.println("  id3 = " + id3);
        System.out.println("  순서 보장: " + (id1 < id2 && id2 < id3));

        // 파싱
        System.out.println("\nParsed id1:");
        System.out.println("  " + parse(id1));

        // 대량 생성 성능 측정
        int count = 100_000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) gen.nextId();
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("%n%,d개 생성 완료: %d ms (%.0f개/ms)%n",
            count, elapsed, (double) count / Math.max(elapsed, 1));
    }
}
