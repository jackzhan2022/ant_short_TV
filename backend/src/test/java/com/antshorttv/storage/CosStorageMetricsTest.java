package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CosStorageMetricsTest {

    @Test
    void recordsRequestsLatencyRetriesAndTransferBytesWithoutObjectIdentityTags() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CosStorageMetrics metrics = new CosStorageMetrics(registry);

        String result = metrics.record("PUT_OBJECT", "upload", 4096L, () -> "stored");
        metrics.retry("HEAD_OBJECT");

        assertThat(result).isEqualTo("stored");
        assertThat(registry.get("antv.cos.requests").tag("operation", "PUT_OBJECT")
            .tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("antv.cos.request.duration").tag("operation", "PUT_OBJECT")
            .tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("antv.cos.transfer.bytes").tag("operation", "PUT_OBJECT")
            .tag("direction", "upload").counter().count()).isEqualTo(4096);
        assertThat(registry.get("antv.cos.retries").tag("operation", "HEAD_OBJECT")
            .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> meter.getId().getTags()
            .forEach(tag -> assertThat(tag.getKey()).isIn("operation", "outcome", "direction")));
    }

    @Test
    void recordsFailuresWithoutSwallowingThem() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CosStorageMetrics metrics = new CosStorageMetrics(registry);

        assertThatThrownBy(() -> metrics.record(
            "GET_OBJECT", "download", 0L, () -> {
                throw new IllegalStateException("https://cos.example/a?sign=secret");
            }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("antv.cos.requests").tag("operation", "GET_OBJECT")
            .tag("outcome", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("antv.cos.failures").tag("operation", "GET_OBJECT")
            .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).extracting(meter -> meter.getId().toString())
            .noneMatch(value -> value.contains("cos.example") || value.contains("secret"));
    }
}
