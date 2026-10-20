package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.execution.AiConcurrencyConfigService;
import com.antshorttv.execution.AiConcurrencySettings;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiProviderDynamicConcurrencyLimiterTest {
    @Test
    void decreaseDrainsExistingUsageAcrossCheckedAndUncheckedCallsAndIncreaseWakesWaiters() throws Exception {
        AtomicInteger limit = new AtomicInteger(2);
        var config = mock(AiConcurrencyConfigService.class);
        when(config.current()).thenAnswer(ignored -> new AiConcurrencySettings(5, 5, 3, 4, limit.get(), 2, 2, 1, 1));
        var limiter = new AiProviderConcurrencyLimiter(config);
        var provider = provider();
        var executor = Executors.newFixedThreadPool(4);
        var entered = new CountDownLatch(2);
        var releaseFirst = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);
        var thirdEntered = new CountDownLatch(1);
        var releaseThird = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> limiter.executeChecked(provider, () -> await(entered, releaseFirst)));
            var second = executor.submit(() -> limiter.executeChecked(provider, () -> await(entered, releaseSecond)));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            limit.set(1);
            var third = executor.submit(() -> limiter.execute(provider, () -> await(thirdEntered, releaseThird)));
            assertThat(thirdEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            assertThat(thirdEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseSecond.countDown();
            second.get(2, TimeUnit.SECONDS);
            assertThat(thirdEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var fourthEntered = new CountDownLatch(1);
            var fourth = executor.submit(() -> limiter.execute(provider, () -> {
                fourthEntered.countDown();
                return "fourth";
            }));
            assertThat(fourthEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
            limit.set(2);
            assertThat(fourthEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fourth.get(2, TimeUnit.SECONDS)).isEqualTo("fourth");
            releaseThird.countDown();
            third.get(2, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            releaseThird.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void interruptedWaiterKeepsInterruptFlagAndDoesNotLeakUsage() throws Exception {
        var limiter = new AiProviderConcurrencyLimiter(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        AtomicBoolean interrupted = new AtomicBoolean();
        var waiting = new CountDownLatch(1);
        var waiter = new Thread(() -> {
            waiting.countDown();
            try {
                limiter.execute(provider(), () -> "must not enter");
            } catch (AiGatewayException exception) {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            var holder = executor.submit(() -> limiter.execute(provider(), () -> await(entered, release)));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            waiter.start();
            assertThat(waiting.await(2, TimeUnit.SECONDS)).isTrue();
            waiter.interrupt();
            waiter.join(2000);
            assertThat(waiter.isAlive()).isFalse();
            assertThat(interrupted).isTrue();
            release.countDown();
            holder.get(2, TimeUnit.SECONDS);
            assertThat(limiter.execute(provider(), () -> "recovered")).isEqualTo("recovered");
        } finally {
            release.countDown();
            waiter.interrupt();
            executor.shutdownNow();
        }
    }

    private static String await(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test release timed out");
            }
            return "done";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static AiProviderEntity provider() {
        var provider = new AiProviderEntity();
        provider.setId(7L);
        return provider;
    }
}
