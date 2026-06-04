package jeong.awsshop.eventpipeline.common;

/**
 * Twitter Snowflake 알고리즘 기반 분산 고유 ID 생성기
 *
 * 64-bit ID 구조:
 * +-----------+------------------+----------+-----------+
 * | 0 (1bit)  | timestamp (41bit)| node(10) | seq (12)  |
 * +-----------+------------------+----------+-----------+
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1_700_000_000_000L;

    private static final long NODE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_NODE_ID = ~(-1L << NODE_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long NODE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this(1L);
    }

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    "nodeId must be between 0 and " + MAX_NODE_ID + ", got: " + nodeId
            );
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long now = currentTimeMs();

        if (now < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards. Refusing to generate id for " + (lastTimestamp - now) + " ms"
            );
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = now;

        return ((now - EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    public static ParsedId parse(long id) {
        long timestampMs = (id >> TIMESTAMP_SHIFT) + EPOCH;
        long node = (id >> NODE_ID_SHIFT) & MAX_NODE_ID;
        long sequence = id & MAX_SEQUENCE;
        return new ParsedId(timestampMs, node, sequence);
    }

    public static long extractTimestampMs(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

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

    public static final class ParsedId {
        public final long timestampMs;
        public final long nodeId;
        public final long sequence;

        ParsedId(long timestampMs, long nodeId, long sequence) {
            this.timestampMs = timestampMs;
            this.nodeId = nodeId;
            this.sequence = sequence;
        }

        @Override
        public String toString() {
            return String.format(
                    "ParsedId{timestamp=%d (%s), nodeId=%d, sequence=%d}",
                    timestampMs,
                    java.time.Instant.ofEpochMilli(timestampMs),
                    nodeId,
                    sequence
            );
        }
    }
}
