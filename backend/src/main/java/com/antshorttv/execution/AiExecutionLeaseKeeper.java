package com.antshorttv.execution;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiExecutionLeaseKeeper {
    private static final Logger LOG = LoggerFactory.getLogger(AiExecutionLeaseKeeper.class);

    private final AiExecutionClaimService claims;
    private final ScheduledExecutorService scheduler;
    private final Duration claimTimeout;
    private final Duration heartbeatInterval;

    @Autowired
    public AiExecutionLeaseKeeper(
        AiExecutionClaimService claims,
        @Value("${ai.execution.claim-timeout:PT10M}") Duration claimTimeout,
        @Value("${ai.execution.heartbeat-interval:PT1M}") Duration heartbeatInterval
    ) {
        validate(claimTimeout, heartbeatInterval);
        this.claims = claims;
        this.claimTimeout = claimTimeout;
        this.heartbeatInterval = heartbeatInterval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-execution-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    AiExecutionLeaseKeeper(
        AiExecutionClaimService claims,
        ScheduledExecutorService scheduler,
        Duration claimTimeout,
        Duration heartbeatInterval
    ) {
        validate(claimTimeout, heartbeatInterval);
        this.claims = claims;
        this.scheduler = scheduler;
        this.claimTimeout = claimTimeout;
        this.heartbeatInterval = heartbeatInterval;
    }

    public Lease keepAlive(AiExecutionClaim claim) {
        ActiveLease lease = new ActiveLease(claim);
        long intervalNanos = heartbeatInterval.toNanos();
        lease.future = scheduler.scheduleAtFixedRate(
            lease, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
        return lease;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private static void validate(Duration claimTimeout, Duration heartbeatInterval) {
        if (claimTimeout == null || claimTimeout.isZero() || claimTimeout.isNegative()) {
            throw new IllegalArgumentException("AI execution claim timeout must be positive.");
        }
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()
            || heartbeatInterval.compareTo(claimTimeout) >= 0) {
            throw new IllegalArgumentException(
                "AI execution heartbeat interval must be positive and shorter than claim timeout.");
        }
    }

    public interface Lease extends AutoCloseable {
        void assertOwned();

        @Override
        void close();
    }

    private final class ActiveLease implements Lease, Runnable {
        private final AiExecutionClaim claim;
        private final AtomicBoolean lost = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        private ActiveLease(AiExecutionClaim claim) {
            this.claim = claim;
        }

        @Override
        public void run() {
            try {
                if (!claims.heartbeat(
                    claim.executionId(), claim.claimToken(), LocalDateTime.now(), claimTimeout)) {
                    loseOwnership();
                }
            } catch (RuntimeException exception) {
                LOG.warn("AI execution heartbeat failed for execution {}", claim.executionId(), exception);
                loseOwnership();
            }
        }

        @Override
        public void assertOwned() {
            if (lost.get()) {
                throw new AiExecutionClaimLostException(claim.executionId());
            }
        }

        @Override
        public void close() {
            ScheduledFuture<?> scheduled = future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }

        private void loseOwnership() {
            lost.set(true);
            close();
        }
    }
}
