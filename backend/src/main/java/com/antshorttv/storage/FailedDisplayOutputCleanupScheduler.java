package com.antshorttv.storage;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FailedDisplayOutputCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(FailedDisplayOutputCleanupScheduler.class);
    private static final int MAX_BATCH_SIZE = 100;
    private final FailedDisplayOutputCleanupStore store;
    private final FailedDisplayOutputCleanupService cleanup;
    private final Clock clock;
    private long afterId;
    private long passUpperId;

    @Autowired
    public FailedDisplayOutputCleanupScheduler(
        FailedDisplayOutputCleanupStore store,
        FailedDisplayOutputCleanupService cleanup
    ) {
        this(store, cleanup, Clock.systemDefaultZone());
    }

    FailedDisplayOutputCleanupScheduler(
        FailedDisplayOutputCleanupStore store,
        FailedDisplayOutputCleanupService cleanup,
        Clock clock
    ) {
        this.store = store;
        this.cleanup = cleanup;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${media.failed-display-cleanup.fixed-delay-ms:3600000}")
    void tick() {
        cleanupExpired(MAX_BATCH_SIZE);
    }

    public synchronized int cleanupExpired(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Failed display cleanup batch must contain 1 to 100 jobs.");
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(14);
        if (passUpperId == 0L) {
            afterId = 0L;
            passUpperId = store.latestCandidateId(cutoff);
            if (passUpperId == 0L) return 0;
        }
        List<Long> candidates = store.candidatesAfter(afterId, passUpperId, cutoff, limit);
        if (candidates.isEmpty()) {
            passUpperId = 0L;
            return 0;
        }
        int cleaned = 0;
        for (Long jobId : candidates) {
            afterId = jobId;
            try {
                if (cleanup.clean(jobId, cutoff)) cleaned++;
            } catch (RuntimeException failure) {
                log.warn("Failed display cleanup deferred: jobId={}", jobId);
            }
        }
        if (afterId >= passUpperId || candidates.size() < limit) passUpperId = 0L;
        return cleaned;
    }
}
