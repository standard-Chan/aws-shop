//package jeong.awsshop.mysql;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.sql.Connection;
//import java.sql.ResultSet;
//import java.sql.Statement;
//import javax.sql.DataSource;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//
//@SpringBootTest
//@ActiveProfiles("test")
//class MysqlConnectionTest {
//
//    @Autowired
//    private DataSource dataSource;
//
//    @Test
//    @DisplayName("test 프로필의 MySQL 데이터베이스에 연결할 수 있어야 한다")
//    void should_connect_to_mysql_database_with_test_profile() throws Exception {
//        try (
//                Connection connection = dataSource.getConnection();
//                Statement statement = connection.createStatement();
//                ResultSet resultSet = statement.executeQuery("SELECT 1")
//        ) {
//            assertThat(connection.isValid(2)).isTrue();
//            assertThat(resultSet.next()).isTrue();
//            assertThat(resultSet.getInt(1)).isEqualTo(1);
//        }
//    }
//}
