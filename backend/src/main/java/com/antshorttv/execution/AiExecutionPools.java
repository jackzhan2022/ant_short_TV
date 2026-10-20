package com.antshorttv.execution;

import jakarta.annotation.PreDestroy;
import java.util.EnumMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Local reservations include tasks between database admission and executor handoff. */
@Component
public class AiExecutionPools implements AutoCloseable {
    private final AiConcurrencyConfigService config;
    private final int publicationWorkers;
    private final EnumMap<AiExecutionPoolType, Pool> pools = new EnumMap<>(AiExecutionPoolType.class);

    @org.springframework.beans.factory.annotation.Autowired
    public AiExecutionPools(AiConcurrencyConfigService config,
        @org.springframework.beans.factory.annotation.Value("${ai.execution.image-publication.worker-threads:2}") int publicationWorkers) {
        this.publicationWorkers = Math.max(1, Math.min(200, publicationWorkers));
        this.config = config;
        for (var type : AiExecutionPoolType.values()) pools.put(type, new Pool(type));
    }

    public AiExecutionPools(AiConcurrencyConfigService config) {
        this(config, 2);
    }

    public Slot tryAcquire(AiExecutionPoolType type) {
        int limit = limit(type);
        Pool pool = pools.get(type);
        synchronized (pool) {
            if (pool.executor.isShutdown() || pool.reserved >= limit) return null;
            pool.reserved++;
            return new Slot(pool, () -> limit(type));
        }
    }

    private int limit(AiExecutionPoolType type) {
        var settings = config.current();
        return switch (type) {
            case TEXT -> settings.textWorkers();
            case IMAGE -> settings.imageWorkers();
            case IMAGE_PUBLICATION -> publicationWorkers;
            case VIDEO_SUBMIT -> settings.videoSubmitWorkers();
            case VIDEO_QUERY -> settings.videoQueryWorkers();
        };
    }

    @PreDestroy
    @Override
    public void close() {
        pools.values().forEach(pool -> pool.executor.shutdown());
    }

    private static final class Pool {
        final ThreadPoolExecutor executor;
        int reserved;

        Pool(AiExecutionPoolType type) {
            var sequence = new AtomicInteger();
            executor = new ThreadPoolExecutor(0, 200, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable,
                        "ai-" + type.name().toLowerCase(java.util.Locale.ROOT) + "-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        }
    }

    public static final class Slot implements AutoCloseable {
        private final Pool pool;
        private final java.util.function.IntSupplier limit;
        private final AtomicBoolean submitted = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        private Slot(Pool pool, java.util.function.IntSupplier limit) {
            this.pool = pool;
            this.limit = limit;
        }

        /** Before a batch worker claims its next task, allow lowered limits to drain. */
        public boolean canContinue() {
            int currentLimit = limit.getAsInt();
            synchronized (pool) {
                return !released.get() && !pool.executor.isShutdown() && pool.reserved <= currentLimit;
            }
        }

        public void submit(Runnable task) {
            if (released.get() || !submitted.compareAndSet(false, true)) {
                throw new IllegalStateException("Execution slot already used or released.");
            }
            try {
                pool.executor.execute(() -> {
                    try { task.run(); } finally { release(); }
                });
            } catch (RuntimeException exception) {
                release();
                throw exception;
            }
        }

        private void release() {
            if (released.compareAndSet(false, true)) {
                synchronized (pool) { pool.reserved--; }
            }
        }

        @Override
        public void close() {
            if (!submitted.get()) release();
        }
    }
}
