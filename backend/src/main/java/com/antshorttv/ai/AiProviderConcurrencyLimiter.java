package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiProviderConcurrencyLimiter {
    private final int maxConcurrency;
    private final ConcurrentHashMap<Long, Semaphore> permitsByProvider = new ConcurrentHashMap<>();

    public AiProviderConcurrencyLimiter(
        @Value("${ai.provider.max-concurrency:4}") int maxConcurrency
    ) {
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public <T> T execute(AiProviderEntity provider, Supplier<T> operation) {
        Semaphore permits = acquire(provider, operation);
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }

    public <T> T executeChecked(AiProviderEntity provider, CheckedOperation<T> operation) throws Exception {
        Semaphore permits = acquire(provider, operation);
        try {
            return operation.execute();
        } finally {
            permits.release();
        }
    }

    private Semaphore acquire(AiProviderEntity provider, Object operation) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(provider.getId(), "provider.id");
        Objects.requireNonNull(operation, "operation");
        Semaphore permits = permitsByProvider.computeIfAbsent(
            provider.getId(), ignored -> new Semaphore(maxConcurrency, true));
        try {
            permits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "等待 AI 服务商并发许可时被中断。");
        }
        return permits;
    }

    @FunctionalInterface
    public interface CheckedOperation<T> {
        T execute() throws Exception;
    }
}
