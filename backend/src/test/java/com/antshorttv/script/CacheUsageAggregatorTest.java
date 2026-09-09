package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CacheUsageAggregatorTest {
    @Test
    void distinguishesKnownZeroFromMissingProviderTelemetry() {
        CacheUsageResponse response = CacheUsageAggregator.aggregate(List.of(
            new CacheUsageAggregator.Call(100, 0),
            new CacheUsageAggregator.Call(200, null)));

        assertThat(response.knownCalls()).isEqualTo(1);
        assertThat(response.unknownCalls()).isEqualTo(1);
        assertThat(response.promptTokens()).isEqualTo(100);
        assertThat(response.cachedInputTokens()).isZero();
        assertThat(response.hitRate()).isZero();
    }

    @Test
    void keepsAllTotalsUnknownWhenNoCallReportsCacheTokens() {
        CacheUsageResponse response = CacheUsageAggregator.aggregate(List.of(
            new CacheUsageAggregator.Call(100, null)));

        assertThat(response.knownCalls()).isZero();
        assertThat(response.unknownCalls()).isEqualTo(1);
        assertThat(response.promptTokens()).isNull();
        assertThat(response.cachedInputTokens()).isNull();
        assertThat(response.hitRate()).isNull();
    }
}
