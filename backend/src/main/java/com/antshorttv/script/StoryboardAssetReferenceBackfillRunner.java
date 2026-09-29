package com.antshorttv.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StoryboardAssetReferenceBackfillRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(
        StoryboardAssetReferenceBackfillRunner.class);
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_START = 20;

    private final StoryboardAssetReferenceBackfillService service;

    public StoryboardAssetReferenceBackfillRunner(
        StoryboardAssetReferenceBackfillService service
    ) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int processed = 0;
        int resolved = 0;
        int pending = 0;
        int unresolved = 0;
        int failed = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_START; batch++) {
            StoryboardAssetReferenceBackfillResult result = service.backfill(BATCH_SIZE);
            processed += result.processed();
            resolved += result.resolved();
            pending += result.pending();
            unresolved += result.unresolved();
            failed += result.failed();
            if (result.processed() == 0) break;
        }
        int drift = service.countConsistencyDrift();
        LOG.info(
            "Storyboard binding backfill completed processed={} resolved={} pending={} "
                + "unresolved={} failed={} drift={}",
            processed, resolved, pending, unresolved, failed, drift);
    }
}
