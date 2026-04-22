package jeong.awsshop.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.review.dto.ReviewBulkUploadRecord;
import jeong.awsshop.review.dto.ReviewDto;
import jeong.awsshop.review.dto.ReviewImageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReviewBulkInsertRepositoryTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement reviewStatement;
    private PreparedStatement imageStatement;
    private ReviewBulkInsertRepository reviewBulkInsertRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Given: JDBC batch insert를 검증하기 위한 mock JDBC 객체를 준비한다
        dataSource = org.mockito.Mockito.mock(DataSource.class);
        connection = org.mockito.Mockito.mock(Connection.class);
        reviewStatement = org.mockito.Mockito.mock(PreparedStatement.class);
        imageStatement = org.mockito.Mockito.mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(reviewStatement, imageStatement);
        when(reviewStatement.executeBatch()).thenReturn(new int[]{1});
        when(imageStatement.executeBatch()).thenReturn(new int[]{1});

        reviewBulkInsertRepository = new ReviewBulkInsertRepository(
                dataSource,
                new SnowflakeIdGenerator(1L)
        );
    }

    @Test
    @DisplayName("유효한 batch가 주어지면 review row를 batch insert 해야 한다")
    void should_insert_review_rows_when_valid_batch_is_given() throws Exception {
        // Given: image가 없는 review record batch
        ReviewBulkUploadRecord record = new ReviewBulkUploadRecord(
                1L,
                "raw-line",
                new ReviewDto(
                        1.0F,
                        "USELESS",
                        "Absolutely useless nonsense and a complete waste of money.",
                        List.of(),
                        "B07G584SHG",
                        "B09WC47S3V",
                        "AEMJ2EG5ODOCYUTI54NBXZHDJGSQ",
                        1602133857705L,
                        2,
                        true
                )
        );

        // When: repository bulk insert를 수행한다
        List<ReviewBulkUploadRecord> failedBatch =
                reviewBulkInsertRepository.bulkInsert(List.of(record));

        // Then: review insert batch가 실행되고 실패 batch는 없어야 한다
        assertThat(failedBatch).isEmpty();
        verify(reviewStatement).addBatch();
        verify(reviewStatement).executeBatch();
        verify(connection).commit();
    }

    @Test
    @DisplayName("image가 있으면 attachment_type을 포함해 review_image row를 batch insert 해야 한다")
    void should_insert_review_image_rows_with_attachment_type_when_records_have_images() throws Exception {
        // Given: attachment_type을 포함한 image record
        ReviewBulkUploadRecord record = new ReviewBulkUploadRecord(
                1L,
                "raw-line",
                new ReviewDto(
                        5.0F,
                        "Big Boy Hearts Bark Box",
                        "There is no other subscription box for dogs like Bark Box.",
                        List.of(new ReviewImageDto(
                                "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL256_.jpg",
                                "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL800_.jpg",
                                "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL1600_.jpg",
                                "IMAGE"
                        )),
                        "B07R7WVRGL",
                        "B08N5QKX1Y",
                        "AEDTXOC3YW6O7P2UPM22VNNRF77A",
                        1563230263551L,
                        3,
                        false
                )
        );

        // When: repository bulk insert를 수행한다
        List<ReviewBulkUploadRecord> failedBatch =
                reviewBulkInsertRepository.bulkInsert(List.of(record));

        // Then: review_image insert에 image URL과 attachment_type이 세팅되어야 한다
        assertThat(failedBatch).isEmpty();
        verify(imageStatement).setString(3, "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL256_.jpg");
        verify(imageStatement).setString(4, "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL800_.jpg");
        verify(imageStatement).setString(5, "https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL1600_.jpg");
        verify(imageStatement).setString(6, "IMAGE");
        verify(imageStatement).addBatch();
        verify(imageStatement).executeBatch();
    }

    @Test
    @DisplayName("parent_asin은 product 조회 없이 review.product_id에 문자열로 세팅해야 한다")
    void should_use_parent_asin_as_review_product_id_without_product_lookup() throws Exception {
        // Given: product가 존재하지 않는 parent_asin을 가진 review record
        ReviewBulkUploadRecord record = new ReviewBulkUploadRecord(
                1L,
                "raw-line",
                new ReviewDto(
                        2.0F,
                        "Manufactured where?",
                        "Tried to contact seller, no luck.",
                        List.of(),
                        "B07QL1JRCN",
                        "PRODUCT_DOES_NOT_EXIST",
                        "AEEJBFZKUBEEMBZUZJV4UHFVEEBQ",
                        1609110735433L,
                        20,
                        true
                )
        );

        // When: repository bulk insert를 수행한다
        reviewBulkInsertRepository.bulkInsert(List.of(record));

        // Then: parent_asin 문자열은 review.product_id parameter로 세팅되어야 한다
        verify(reviewStatement).setString(3, "PRODUCT_DOES_NOT_EXIST");

        // Then: product 조회는 null을 반환해야한다. (Product가 존재해서는 안된다)
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection, org.mockito.Mockito.times(2)).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .noneSatisfy(sql -> assertThat(sql.toLowerCase()).contains(" from product"));
    }

    @Test
    @DisplayName("review_image insert가 실패하면 batch transaction을 rollback하고 실패 batch를 반환해야 한다")
    void should_rollback_entire_batch_when_review_image_insert_fails() throws Exception {
        // Given: image insert에서 DB 에러가 발생하도록 준비한다
        when(imageStatement.executeBatch()).thenThrow(new SQLException("image insert failed"));
        ReviewBulkUploadRecord record = new ReviewBulkUploadRecord(
                1L,
                "raw-line",
                new ReviewDto(
                        5.0F,
                        "Image failure",
                        "image insert should rollback",
                        List.of(new ReviewImageDto("small", "medium", "large", "IMAGE")),
                        "B000IMAGE",
                        "PARENT_IMAGE",
                        "USER_IMAGE",
                        1602133857705L,
                        0,
                        true
                )
        );

        // When: repository bulk insert를 수행한다
        List<ReviewBulkUploadRecord> failedBatch =
                reviewBulkInsertRepository.bulkInsert(List.of(record));

        // Then: transaction rollback 후 실패 batch list를 반환해야 한다
        assertThat(failedBatch).containsExactly(record);
        verify(connection).rollback();
    }

    @Test
    @DisplayName("DB insert가 실패하면 실패 batch list를 반환해야 한다")
    void should_return_failed_batch_list_when_db_insert_fails() throws Exception {
        // Given: review insert에서 DB 에러가 발생하도록 준비한다
        when(reviewStatement.executeBatch()).thenThrow(new SQLException("review insert failed"));
        ReviewBulkUploadRecord record = new ReviewBulkUploadRecord(
                1L,
                "raw-line",
                new ReviewDto(
                        1.0F,
                        "DB failure",
                        "review insert should return failed batch",
                        List.of(),
                        "B000FAIL",
                        "PARENT_FAIL",
                        "USER_FAIL",
                        1602133857705L,
                        0,
                        true
                )
        );

        // When: repository bulk insert를 수행한다
        List<ReviewBulkUploadRecord> failedBatch =
                reviewBulkInsertRepository.bulkInsert(List.of(record));

        // Then: service가 실패 JSONL로 저장할 수 있도록 실패 batch를 반환해야 한다
        assertThat(failedBatch).containsExactly(record);
        verify(connection).rollback();
    }

    @Test
    @DisplayName("batch size보다 적은 잔여 record도 insert 해야 한다")
    void should_insert_remaining_records_when_record_count_is_less_than_batch_size() throws Exception {
        // Given: service batch size보다 적게 남은 마지막 review record
        ReviewBulkUploadRecord record = new ReviewBulkUploadRecord(
                1L,
                "raw-line",
                new ReviewDto(
                        4.0F,
                        "Remaining batch",
                        "last partial batch should be inserted",
                        List.of(new ReviewImageDto("small", "medium", "large", "IMAGE")),
                        "B000LAST",
                        "PARENT_LAST",
                        "USER_LAST",
                        1602133857705L,
                        1,
                        true
                )
        );

        // When: batch size보다 적은 record list를 insert 한다
        List<ReviewBulkUploadRecord> failedBatch =
                reviewBulkInsertRepository.bulkInsert(List.of(record));

        // Then: 잔여 record도 review와 review_image에 insert되어야 한다
        assertThat(failedBatch).isEmpty();
        verify(reviewStatement).executeBatch();
        verify(imageStatement).executeBatch();
        verify(connection).commit();
    }
}
