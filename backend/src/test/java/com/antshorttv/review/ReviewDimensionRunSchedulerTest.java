package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void waitsForEveryStartedDimensionBeforePropagatingFailure() {
        ReviewDimensionRunScheduler scheduler = new ReviewDimensionRunScheduler();
        CountDownLatch peerStarted = new CountDownLatch(1);
        AtomicBoolean peerFinished = new AtomicBoolean();

        assertThatThrownBy(() -> scheduler.execute(List.of(1, 2, 3), 2, dimension -> {
            if (dimension == 1) return dimension;
            if (dimension == 2) {
                try {
                    assertThat(peerStarted.await(2, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                throw new IllegalStateException("dimension failed");
            }
            peerStarted.countDown();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
            while (System.nanoTime() < deadline) {
                try {
                    TimeUnit.NANOSECONDS.sleep(deadline - System.nanoTime());
                } catch (InterruptedException ignored) {
                    // Simulate a provider/database operation that reaches its durable terminal write.
                }
            }
            peerFinished.set(true);
            return dimension;
        })).isInstanceOf(IllegalStateException.class).hasMessage("dimension failed");

        assertThat(peerFinished).isTrue();
    }

    @Test
    void interruptedCallerStillJoinsEveryStartedDimensionBeforeReturning() throws Exception {
        ReviewDimensionRunScheduler scheduler = new ReviewDimensionRunScheduler();
        CountDownLatch peerStarted = new CountDownLatch(1);
        AtomicBoolean peerFinished = new AtomicBoolean();
        AtomicBoolean callerReturnedAfterPeer = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            assertThatThrownBy(() -> scheduler.execute(List.of(1, 2), 1, dimension -> {
                if (dimension == 1) return dimension;
                peerStarted.countDown();
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
                while (System.nanoTime() < deadline) {
                    try { TimeUnit.NANOSECONDS.sleep(deadline - System.nanoTime()); }
                    catch (InterruptedException ignored) { }
                }
                peerFinished.set(true);
                return dimension;
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("中断");
            callerReturnedAfterPeer.set(peerFinished.get());
        });
        caller.start();
        assertThat(peerStarted.await(2, TimeUnit.SECONDS)).isTrue();
        caller.interrupt();
        caller.join(2000);

        assertThat(caller.isAlive()).isFalse();
        assertThat(callerReturnedAfterPeer).isTrue();
    }
}
