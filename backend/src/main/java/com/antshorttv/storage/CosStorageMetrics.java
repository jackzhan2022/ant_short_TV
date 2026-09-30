package com.antshorttv.storage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
class CosStorageMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger internalEndpoint = new AtomicInteger();

    CosStorageMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("antv.cos.internal.endpoint", internalEndpoint, AtomicInteger::get)
            .description("Whether the COS client is configured for a same-region internal endpoint")
            .register(registry);
    }

    <T> T record(
        String operation,
        String direction,
        long bytes,
        CosCall<T> call
    ) throws Exception {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            T result = call.execute();
            if (bytes > 0 && direction != null && !direction.isBlank()) {
                Counter.builder("antv.cos.transfer.bytes")
                    .tag("operation", operation)
                    .tag("direction", direction)
                    .register(registry)
                    .increment(bytes);
            }
            return result;
        } catch (Exception exception) {
            outcome = "failure";
            Counter.builder("antv.cos.failures")
                .tag("operation", operation)
                .register(registry)
                .increment();
            throw exception;
        } finally {
            Counter.builder("antv.cos.requests")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
            sample.stop(Timer.builder("antv.cos.request.duration")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry));
        }
    }

    void retry(String operation) {
        Counter.builder("antv.cos.retries")
            .tag("operation", operation)
            .register(registry)
            .increment();
    }

    void internalEndpointConfigured(boolean configured) {
        internalEndpoint.set(configured ? 1 : 0);
    }

    @FunctionalInterface
    interface CosCall<T> {
        T execute() throws Exception;
    }
}
