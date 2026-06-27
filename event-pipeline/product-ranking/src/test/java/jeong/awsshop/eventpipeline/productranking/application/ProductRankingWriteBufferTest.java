package jeong.awsshop.eventpipeline.productranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.infrastructure.clickhouse.ClickHouseProductRankingStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ProductRankingWriteBufferTest {

    @Test
    @DisplayName("save는 점수 변경분을 큐에 넣고 저장소를 바로 호출하지 않아야 한다")
    void should_enqueue_delta_without_calling_store_immediately() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        ProductRankingWriteBuffer buffer = new ProductRankingWriteBuffer(
                1000,
                1000,
                100000,
                store,
                Executors.newSingleThreadScheduledExecutor()
        );

        buffer.save(delta(100L, 3L));

        assertThat(buffer.queuedDeltaCount()).isEqualTo(1);
        verify(store, never()).increaseScores(anyList());
        buffer.stop();
    }

    @Test
    @DisplayName("flush 시 저장소를 batch 단위로 호출해야 한다")
    void should_save_batch_on_flush() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        ProductRankingWriteBuffer buffer = new ProductRankingWriteBuffer(
                1000,
                1000,
                100000,
                store,
                Executors.newSingleThreadScheduledExecutor()
        );

        buffer.save(delta(100L, 3L));
        buffer.save(delta(100L, 1L));
        buffer.save(delta(200L, 10L));
        buffer.flushSafely();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductRankingScoreDelta>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).increaseScores(captor.capture());
        assertThat(captor.getValue())
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 4L),
                        tuple(200L, 10L)
                );
        assertThat(buffer.queuedDeltaCount()).isZero();
        buffer.stop();
    }

    @Test
    @DisplayName("flush 1회는 큐가 계속 채워져도 batch 크기만큼만 저장소에 전달해야 한다")
    void should_flush_only_one_batch_per_flush_call() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        ProductRankingWriteBuffer buffer = new ProductRankingWriteBuffer(
                2,
                1000,
                100000,
                store,
                executor
        );

        buffer.save(delta(100L, 3L));
        buffer.save(delta(200L, 1L));
        buffer.save(delta(300L, 10L));
        buffer.flushSafely();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductRankingScoreDelta>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).increaseScores(captor.capture());
        assertThat(captor.getValue())
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 3L),
                        tuple(200L, 1L)
                );
        assertThat(buffer.queuedDeltaCount()).isEqualTo(1);
        buffer.stop();
    }

    @Test
    @DisplayName("ClickHouse 저장소가 있으면 같은 압축 batch를 병행 적재해야 한다")
    void should_save_compressed_batch_to_clickhouse_when_enabled() {
        ProductRankingStore redisStore = Mockito.mock(ProductRankingStore.class);
        ClickHouseProductRankingStore clickHouseStore = Mockito.mock(ClickHouseProductRankingStore.class);
        ProductRankingWriteBuffer buffer = new ProductRankingWriteBuffer(
                1000,
                1000,
                100000,
                redisStore,
                clickHouseStore,
                Executors.newSingleThreadScheduledExecutor()
        );

        buffer.save(delta(100L, 3L));
        buffer.save(delta(100L, 1L));
        buffer.save(delta(200L, 10L));
        buffer.flushSafely();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductRankingScoreDelta>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickHouseStore).increaseScores(captor.capture());
        assertThat(captor.getValue())
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 4L),
                        tuple(200L, 10L)
                );
        buffer.stop();
    }

    private ProductRankingScoreDelta delta(Long productId, long score) {
        return new ProductRankingScoreDelta(productId, score, Instant.parse("2026-06-07T06:00:00Z"));
    }
}
