package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiConcurrencyConfigService;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiProviderConcurrencyLimiter {
    private final IntSupplier maxConcurrency;
    // One gate per provider shared by all modalities in this application JVM; not a distributed limit.
    private final ConcurrentHashMap<Long, ProviderGate> permitsByProvider = new ConcurrentHashMap<>();

    @Autowired
    public AiProviderConcurrencyLimiter(AiConcurrencyConfigService settings) {
        this.maxConcurrency = () -> settings.current().providerLimit();
    }

    public AiProviderConcurrencyLimiter(int maxConcurrency) {
        this.maxConcurrency = () -> Math.max(1, maxConcurrency);
    }

    public <T> T execute(AiProviderEntity provider, Supplier<T> operation) {
        ProviderGate permits = acquire(provider, operation);
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }

    public <T> T executeChecked(AiProviderEntity provider, CheckedOperation<T> operation) throws Exception {
        ProviderGate permits = acquire(provider, operation);
        try {
            return operation.execute();
        } finally {
            permits.release();
        }
    }

    private ProviderGate acquire(AiProviderEntity provider, Object operation) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(provider.getId(), "provider.id");
        Objects.requireNonNull(operation, "operation");
        ProviderGate permits = permitsByProvider.computeIfAbsent(provider.getId(), ignored -> new ProviderGate());
        try {
            permits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "等待 AI 服务商并发许可时被中断。");
        }
        return permits;
    }

    private class ProviderGate {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition changed = lock.newCondition();
        private int inUse;

        void acquire() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (inUse >= maxConcurrency.getAsInt()) {
                    // Recheck persisted settings even if no call completes while the limit is raised.
                    changed.await(100, TimeUnit.MILLISECONDS);
                }
                inUse++;
            } finally {
                lock.unlock();
            }
        }

        void release() {
            lock.lock();
            try {
                inUse--;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    @FunctionalInterface
    public interface CheckedOperation<T> {
        T execute() throws Exception;
    }
}
