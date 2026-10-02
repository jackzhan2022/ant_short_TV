package com.antshorttv.storage;

import java.time.LocalDateTime;
import java.util.List;

abstract class FailedDisplayOutputCleanupStore {
    abstract long latestCandidateId(LocalDateTime cutoff);
    abstract List<Long> candidatesAfter(long afterId, long upperId, LocalDateTime cutoff, int limit);
    abstract MediaProcessingJobEntity jobForUpdate(Long jobId);
    abstract MediaObjectEntity media(Long mediaId);
    abstract MediaObjectEntity original(String inputKey);
    abstract boolean cleaned(Long jobId, int attemptNo);
    abstract void markCleaned(Long jobId, int attemptNo);
}
