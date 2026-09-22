package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiProviderConcurrencyLimiterTest {

    @Test
    void limitsConcurrentCallsForTheSameProvider() throws Exception {
        AiProviderConcurrencyLimiter limiter = new AiProviderConcurrencyLimiter(2);
        AiProviderEntity provider = provider(7L);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch firstTwoEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> limiter.execute(provider, () -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    firstTwoEntered.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                        return current;
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    } finally {
                        active.decrementAndGet();
                    }
                })));
            }

            assertThat(firstTwoEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(active.get()).isEqualTo(2);
            release.countDown();
            for (Future<Integer> future : futures) {
                future.get(2, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(maximum.get()).isEqualTo(2);
    }

    @Test
    void keepsSeparateLimitsForDifferentProviders() throws Exception {
        AiProviderConcurrencyLimiter limiter = new AiProviderConcurrencyLimiter(1);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> limiter.execute(provider(7L), () -> waitForRelease(bothEntered, release)));
            Future<?> second = executor.submit(() -> limiter.execute(provider(8L), () -> waitForRelease(bothEntered, release)));

            assertThat(bothEntered.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releasesPermitWhenProviderCallFails() {
        AiProviderConcurrencyLimiter limiter = new AiProviderConcurrencyLimiter(1);
        AiProviderEntity provider = provider(7L);

        assertThatThrownBy(() -> limiter.execute(provider, () -> {
            throw new IllegalStateException("provider failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(limiter.execute(provider, () -> "recovered")).isEqualTo("recovered");
    }

    private String waitForRelease(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            release.await(2, TimeUnit.SECONDS);
            return "done";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private AiProviderEntity provider(long id) {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(id);
        return provider;
    }
}
