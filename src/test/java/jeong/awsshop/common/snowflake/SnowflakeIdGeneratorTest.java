package jeong.awsshop.common.snowflake;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("생성 순서대로 ID 증가 순서가 보장되어야 한다")
    void should_generate_ids_in_strictly_increasing_order() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        long firstId = generator.nextId();
        long secondId = generator.nextId();
        long thirdId = generator.nextId();

        assertThat(firstId).isLessThan(secondId);
        assertThat(secondId).isLessThan(thirdId);
    }
}
