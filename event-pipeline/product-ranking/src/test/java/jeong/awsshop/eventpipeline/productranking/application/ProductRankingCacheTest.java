package jeong.awsshop.eventpipeline.productranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductRankingCacheTest {

    private static final Instant NOW = Instant.parse("2026-06-07T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("윈도우별 Top100을 저장소에서 읽어 캐시에 저장해야 한다")
    void should_refresh_top_100_rankings_by_window() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        stubEmptyRankings(store);
        when(store.findTop(RankingWindow.ONE_HOUR, 100, NOW))
                .thenReturn(List.of(new ProductRankingItem(1L, 100L, 14L)));
        ProductRankingCache cache = cache(store);

        cache.refreshSafely();

        assertThat(cache.findTop(RankingWindow.ONE_HOUR, 10))
                .extracting("rank", "productId", "score")
                .containsExactly(tuple(1L, 100L, 14L));
        verify(store).findTop(RankingWindow.ONE_HOUR, 100, NOW);
        verify(store).findTop(RankingWindow.ONE_DAY, 100, NOW);
        verify(store).findTop(RankingWindow.ONE_WEEK, 100, NOW);
        cache.stop();
    }

    @Test
    @DisplayName("요청 limit만큼 캐시 snapshot을 잘라 반환해야 한다")
    void should_slice_cached_rankings_by_limit() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        stubEmptyRankings(store);
        when(store.findTop(RankingWindow.ONE_HOUR, 100, NOW))
                .thenReturn(List.of(
                        new ProductRankingItem(1L, 100L, 14L),
                        new ProductRankingItem(2L, 200L, 10L),
                        new ProductRankingItem(3L, 300L, 3L)
                ));
        ProductRankingCache cache = cache(store);
        cache.refreshSafely();

        assertThat(cache.findTop(RankingWindow.ONE_HOUR, 2))
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 14L),
                        tuple(200L, 10L)
                );
        assertThat(cache.findTop(RankingWindow.ONE_HOUR, 1))
                .extracting("productId", "score")
                .containsExactly(tuple(100L, 14L));
        verify(store, times(1)).findTop(RankingWindow.ONE_HOUR, 100, NOW);
        cache.stop();
    }

    @Test
    @DisplayName("갱신 실패 시 기존 캐시를 유지해야 한다")
    void should_keep_previous_cache_on_refresh_failure() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        stubEmptyRankings(store);
        when(store.findTop(RankingWindow.ONE_HOUR, 100, NOW))
                .thenReturn(List.of(new ProductRankingItem(1L, 100L, 14L)));
        ProductRankingCache cache = cache(store);
        cache.refreshSafely();

        when(store.findTop(RankingWindow.ONE_HOUR, 100, NOW))
                .thenThrow(new IllegalStateException("redis unavailable"));
        cache.refreshSafely();

        assertThat(cache.findTop(RankingWindow.ONE_HOUR, 10))
                .extracting("productId", "score")
                .containsExactly(tuple(100L, 14L));
        cache.stop();
    }

    @Test
    @DisplayName("초기 캐시가 없는 상태에서 갱신 실패 시 빈 목록을 반환해야 한다")
    void should_return_empty_list_when_initial_refresh_fails() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        stubEmptyRankings(store);
        when(store.findTop(RankingWindow.ONE_HOUR, 100, NOW))
                .thenThrow(new IllegalStateException("redis unavailable"));
        ProductRankingCache cache = cache(store);

        cache.refreshSafely();

        assertThat(cache.findTop(RankingWindow.ONE_HOUR, 10)).isEmpty();
        cache.stop();
    }

    @Test
    @DisplayName("실패 후 다음 갱신 성공 시 캐시를 채워야 한다")
    void should_refresh_cache_after_failure_recovers() {
        ProductRankingStore store = Mockito.mock(ProductRankingStore.class);
        stubEmptyRankings(store);
        when(store.findTop(RankingWindow.ONE_HOUR, 100, NOW))
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn(List.of(new ProductRankingItem(1L, 100L, 14L)));
        ProductRankingCache cache = cache(store);

        cache.refreshSafely();
        cache.refreshSafely();

        assertThat(cache.findTop(RankingWindow.ONE_HOUR, 10))
                .extracting("productId", "score")
                .containsExactly(tuple(100L, 14L));
        cache.stop();
    }

    private ProductRankingCache cache(ProductRankingStore store) {
        return new ProductRankingCache(
                store,
                CLOCK,
                1000,
                Executors.newSingleThreadScheduledExecutor()
        );
    }

    private void stubEmptyRankings(ProductRankingStore store) {
        when(store.findTop(any(RankingWindow.class), eq(100), eq(NOW))).thenReturn(List.of());
    }
}
