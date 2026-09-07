package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReviewDimensionRunSchedulerTest {

    @Test
    void completesFirstDimensionAsCacheWarmupBeforeStartingParallelDimensions() {
        ReviewDimensionRunScheduler scheduler = new ReviewDimensionRunScheduler();
        AtomicBoolean warmed = new AtomicBoolean();
        CountDownLatch parallelStarted = new CountDownLatch(2);

        List<Integer> results = scheduler.execute(List.of(1, 2, 3), 2, dimension -> {
            if (dimension == 1) {
                warmed.set(true);
                return dimension;
            }
            assertThat(warmed).isTrue();
            parallelStarted.countDown();
            try {
                assertThat(parallelStarted.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS))
                    .isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return dimension;
        });

        assertThat(results).containsExactly(1, 2, 3);
    }

    @Test
    void neverRunsMoreDimensionsThanConfiguredConcurrency() {
        ReviewDimensionRunScheduler scheduler = new ReviewDimensionRunScheduler();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        scheduler.execute(List.of(1, 2, 3, 4, 5), 2, dimension -> {
            int running = active.incrementAndGet();
            peak.accumulateAndGet(running, Math::max);
            try {
                Thread.sleep(40);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                active.decrementAndGet();
            }
            return dimension;
        });

        assertThat(peak).hasValue(2);
    }
}
