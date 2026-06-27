package jeong.awsshop.eventpipeline.productranking.infrastructure.clickhouse;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * ClickHouse에 상품 랭킹 점수 원본과 분 단위 집계 데이터를 저장하고 조회하는 infrastructure adapter다.
 *
 * <p>현재 구현은 초기 도입 단계라 Spring Data/JPA를 쓰지 않고 ClickHouse JDBC를 직접 사용한다.
 * {@link #connection()}은 {@link DriverManager}로 호출 때마다 새 JDBC connection을 연다.
 * 이 방식은 코드가 단순하지만, JPA/Hibernate의 일반적인 connection 관리 방식과 다르다.
 * JPA는 보통 HikariCP 같은 {@code DataSource} connection pool에서 connection을 빌려 쓰고 반납한다.
 *
 * <p>따라서 이 클래스의 connection 방식은 "기능 검증용 첫 구현"에 가깝고,
 * 부하 테스트/운영 단계에서는 ClickHouse용 {@code DataSource} Bean을 만들고 pool을 통해 connection을 재사용하는 구조가 더 적합하다.
 */
@Repository
@ConditionalOnProperty(
        prefix = "event-pipeline.product-ranking.clickhouse",
        name = "enabled",
        havingValue = "true"
)
public class ClickHouseProductRankingStore {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseProductRankingStore.class);
    // ProductRankingScoreDelta는 이미 점수로 환산된 값이라 원본 사용자 행동 타입 대신 내부 식별자를 저장한다.
    private static final String RANKING_SCORE_EVENT_TYPE = "RANKING_SCORE";
    private static final Duration BUCKET_SIZE = Duration.ofMinutes(1);
    private static final String SCHEMA_RESOURCE = "/db/clickhouse/product-ranking-schema.sql";

    private final ClickHouseProductRankingProperties properties;
    // 기동 시 ClickHouse가 아직 준비되지 않을 수 있으므로 insert/query 경로에서도 schema 생성을 재시도한다.
    private final AtomicBoolean schemaInitialized = new AtomicBoolean(false);

    public ClickHouseProductRankingStore(ClickHouseProductRankingProperties properties) {
        this.properties = properties;
    }

    /**
     * 애플리케이션 기동 직후 ClickHouse schema 생성을 1회 시도한다.
     *
     * <p>로컬 compose에서는 애플리케이션이 ClickHouse보다 먼저 뜰 수 있다.
     * 이 시점의 연결 실패가 서버 기동 실패로 이어지면 개발/테스트가 불편하므로,
     * 실제 저장/조회 진입점에서 {@link #initializeSchemaIfNeeded()}로 다시 시도하게 한다.
     */
    @PostConstruct
    void initializeSchema() {
        log.info("ClickHouse product ranking store 초기화를 시작합니다.");
        initializeSchemaIfNeeded();
    }

    /**
     * schema 자동 초기화가 켜져 있고 아직 성공한 적이 없을 때만 DDL을 실행한다.
     *
     * <p>DDL은 {@code CREATE TABLE IF NOT EXISTS}라서 테이블이 이미 있으면 아무 것도 만들지 않고,
     * 기존 데이터도 삭제하지 않는다. 즉 데이터가 존재하는 상태에서 이 함수가 호출돼도 truncate/drop은 하지 않는다.
     *
     * <p>한계도 있다. 기존 테이블이 다른 구조로 존재해도 {@code IF NOT EXISTS} 때문에 schema mismatch를 감지하지 못한다.
     * 운영에서는 애플리케이션 코드가 DDL을 직접 실행하기보다 Flyway/Liquibase, compose init script,
     * 배포 migration job 같은 별도 절차를 쓰는 편이 더 안전하다.
     */
    private void initializeSchemaIfNeeded() {
        // 운영에서 Flyway/별도 DDL 배포를 쓰는 경우 application의 schema 생성을 끌 수 있다.
        if (!properties.initializeSchema()) {
            return;
        }
        if (schemaInitialized.get()) {
            return;
        }

        log.warn("ClickHouse product ranking schema 초기화를 시작합니다.");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String sql : schemaStatements()) {
                statement.execute(sql);
            }
            schemaInitialized.set(true);
        } catch (RuntimeException | SQLException exception) {
            log.warn("ClickHouse product ranking schema 초기화에 실패했습니다. 이후 batch/query 경로에서 재시도합니다.", exception);
        }
    }

    /**
     * ranking score delta batch를 ClickHouse에 저장한다.
     *
     * <p>같은 batch를 두 테이블에 넣는다.
     * {@code product_ranking_events}는 재집계/검증/복구를 위한 원본성 데이터이고,
     * {@code product_ranking_minute_scores}는 window ranking 조회를 빠르게 하기 위한 분 단위 read model이다.
     *
     * <p>현재는 호출마다 {@link #connection()}으로 새 JDBC connection을 연다.
     * 이 말은 매 batch마다 URL/계정/비밀번호를 사용해 물리 connection 생성을 시도한다는 뜻이다.
     * 기능 구현은 단순하지만, connection 생성 비용 때문에 트래픽이 많으면 느려질 수 있다.
     * JPA처럼 pool을 쓰려면 {@code DataSource}를 주입받고 try-with-resources로 pool에서 빌린 connection을 반납하는 구조로 바꿔야 한다.
     */
    public void increaseScores(List<ProductRankingScoreDelta> deltas) {
        if (deltas.isEmpty()) {
            return;
        }

        initializeSchemaIfNeeded();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            // 원본 점수 이벤트는 재집계/검증/장애 복구용으로 남긴다.
            insertRawEvents(connection, deltas);
            // API 랭킹 조회는 매번 원본 전체를 읽지 않도록 분 단위 집계 테이블을 별도로 채운다.
            insertMinuteScores(connection, deltas);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("ClickHouse 상품 랭킹 batch 적재에 실패했습니다.", exception);
        }
    }

    /**
     * 지정한 window에 포함되는 분 단위 bucket 점수를 product_id별로 합산해 Top N을 조회한다.
     *
     * <p>Redis와 결과 정책을 맞추기 위해 score 내림차순, 동점이면 product_id 오름차순으로 정렬한다.
     * {@code SummingMergeTree}는 같은 key의 numeric 값을 백그라운드 merge에서 합쳐주지만, merge 시점은 비동기다.
     * 그래서 저장 시 합쳐졌다고 가정하지 않고 조회 SQL에서도 항상 {@code sum(score) GROUP BY product_id}를 수행한다.
     *
     * <p>이 함수도 현재는 조회마다 새 JDBC connection을 연다.
     * API 요청마다 직접 호출되는 것은 아니고 cache refresh 주기에 호출되지만,
     * refresh 주기가 짧거나 서버 인스턴스가 많으면 connection pool 적용이 필요하다.
     */
    public List<ProductRankingItem> findTop(RankingWindow window, int limit, Instant now) {
        if (limit <= 0) {
            return List.of();
        }

        initializeSchemaIfNeeded();
        // SummingMergeTree merge는 비동기라 이미 같은 key가 합쳐졌다고 가정하지 않고 항상 sum(score)로 조회한다.
        String sql = """
                SELECT product_id, sum(score) AS total_score
                FROM product_ranking_minute_scores
                WHERE bucket_start_at >= ?
                  AND bucket_start_at <= ?
                GROUP BY product_id
                ORDER BY total_score DESC, product_id ASC
                LIMIT ?
                """;

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            // Redis bucket 범위와 맞추기 위해 window 시작/끝 시각을 모두 분 단위 bucket으로 내린 뒤 포함 조회한다.
            statement.setTimestamp(1, Timestamp.from(bucketStart(now.minus(window.duration()))));
            statement.setTimestamp(2, Timestamp.from(bucketStart(now)));
            statement.setInt(3, limit);

            List<ProductRankingItem> rankings = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                long rank = 1L;
                while (resultSet.next()) {
                    rankings.add(new ProductRankingItem(
                            rank++,
                            resultSet.getLong("product_id"),
                            resultSet.getLong("total_score")
                    ));
                }
            }
            return rankings;
        } catch (SQLException exception) {
            throw new IllegalStateException("ClickHouse 상품 랭킹 조회에 실패했습니다. window=" + window, exception);
        }
    }

    /**
     * 원본 score event table에 batch insert한다.
     *
     * <p>현재 {@link ProductRankingScoreDelta}에는 원래 사용자 행동 이벤트 타입이 없고 이미 점수로 변환된 값만 있다.
     * 그래서 event_type에는 임시 내부 값인 {@code RANKING_SCORE}를 저장한다.
     * SEARCH/PRODUCT_VIEW/ADD_TO_CART까지 ClickHouse에 보존하려면 delta 모델이나 ClickHouse 전용 write model을 확장해야 한다.
     */
    private void insertRawEvents(Connection connection, List<ProductRankingScoreDelta> deltas) throws SQLException {
        String sql = """
                INSERT INTO product_ranking_events
                    (occurred_at, product_id, event_type, score)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProductRankingScoreDelta delta : deltas) {
                statement.setTimestamp(1, Timestamp.from(delta.occurredAt()));
                statement.setLong(2, delta.productId());
                statement.setString(3, RANKING_SCORE_EVENT_TYPE);
                statement.setLong(4, delta.score());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * window ranking 조회용 분 단위 집계 table에 batch insert한다.
     *
     * <p>bucket 기준은 Redis와 맞추기 위해 occurredAt을 1분 시작 시각으로 내림한다.
     * 같은 product_id와 bucket_start_at row가 여러 번 들어가도 조회 SQL에서 다시 합산하므로 결과는 유지된다.
     */
    private void insertMinuteScores(Connection connection, List<ProductRankingScoreDelta> deltas) throws SQLException {
        String sql = """
                INSERT INTO product_ranking_minute_scores
                    (bucket_start_at, product_id, score)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProductRankingScoreDelta delta : deltas) {
                statement.setTimestamp(1, Timestamp.from(bucketStart(delta.occurredAt())));
                statement.setLong(2, delta.productId());
                statement.setLong(3, delta.score());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Instant를 Redis bucket과 동일한 1분 bucket 시작 시각으로 변환한다.
     *
     * <p>예를 들어 06:03:45.123Z는 06:03:00Z bucket에 들어간다.
     */
    private Instant bucketStart(Instant instant) {
        // ClickHouse 집계 기준을 Redis의 1분 bucket과 맞춘다.
        long bucketMillis = instant.toEpochMilli() / BUCKET_SIZE.toMillis() * BUCKET_SIZE.toMillis();
        return Instant.ofEpochMilli(bucketMillis);
    }

    /**
     * ClickHouse JDBC connection을 생성한다.
     *
     * <p>이 함수는 properties의 URL, username, password로 매번 새 connection을 연다.
     * JPA의 일반적인 방식과 다르다. JPA/Spring Boot는 보통 HikariCP {@code DataSource}를 만들고,
     * repository나 transaction 실행 시 pool에서 이미 만들어진 connection을 빌려 쓴 뒤 반납한다.
     *
     * <p>따라서 이 함수는 단순한 초기 구현에는 충분하지만, 성능 면에서는 좋지 않다.
     * ClickHouse를 본격적으로 쓰려면 이 함수 대신 ClickHouse {@code DataSource} 또는 Spring {@code JdbcTemplate}을 두고
     * connection pool을 사용해야 한다.
     */
    private Connection connection() throws SQLException {
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", properties.username());
        connectionProperties.setProperty("password", properties.password());
        // clickhouse-jdbc는 LZ4 compression을 사용할 수 있는데, 현재 classpath에는 LZ4 라이브러리가 없다.
        // 로컬 기본 실행에서는 compression을 꺼서 별도 optional dependency 없이 연결되게 한다.
        connectionProperties.setProperty("compress", "0");
        return DriverManager.getConnection(properties.url(), connectionProperties);
    }

    /**
     * classpath의 ClickHouse schema SQL 파일을 읽어 개별 statement 목록으로 나눈다.
     *
     * <p>현재 schema 파일은 단순한 {@code CREATE TABLE} 두 개라 세미콜론 split으로 충분하다.
     * 복잡한 DDL이나 세미콜론이 포함된 문자열이 들어가면 이 방식은 깨질 수 있으므로,
     * 그 단계에서는 migration 도구를 사용하는 편이 안전하다.
     */
    private List<String> schemaStatements() {
        try (var inputStream = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("ClickHouse schema resource를 찾을 수 없습니다: " + SCHEMA_RESOURCE);
            }
            String schema = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            // schema 파일에는 여러 CREATE TABLE 문이 있으므로 세미콜론 단위로 나눠 순서대로 실행한다.
            return List.of(schema.split(";")).stream()
                    .map(String::trim)
                    .filter(sql -> !sql.isBlank())
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("ClickHouse schema resource를 읽을 수 없습니다.", exception);
        }
    }
}
