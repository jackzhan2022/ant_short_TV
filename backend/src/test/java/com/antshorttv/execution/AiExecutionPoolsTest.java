package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AiExecutionPoolsTest {
    @Test
    void reservesBeforeDispatchAndKeepsTypesIndependentWhileLimitsDrain() throws Exception {
        var config = mock(AiConcurrencyConfigService.class);
        when(config.current()).thenReturn(settings(2));
        try (var pools = new AiExecutionPools(config)) {
            var first = pools.tryAcquire(AiExecutionPoolType.TEXT);
            var second = pools.tryAcquire(AiExecutionPoolType.TEXT);
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(pools.tryAcquire(AiExecutionPoolType.TEXT)).isNull();
            var image = pools.tryAcquire(AiExecutionPoolType.IMAGE);
            assertThat(image).isNotNull();
            image.close();
            when(config.current()).thenReturn(settings(1));
            assertThat(first.canContinue()).isFalse();
            first.close();
            first.close();
            assertThat(pools.tryAcquire(AiExecutionPoolType.TEXT)).isNull();
            assertThat(second.canContinue()).isTrue();
            var finished = new CountDownLatch(1);
            second.submit(finished::countDown);
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private AiConcurrencySettings settings(int text) {
        return new AiConcurrencySettings(5, 5, 3, 4, 4, text, 2, 1, 1);
    }
}
