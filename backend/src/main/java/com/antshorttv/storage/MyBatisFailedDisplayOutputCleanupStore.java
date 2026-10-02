package com.antshorttv.storage;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MyBatisFailedDisplayOutputCleanupStore extends FailedDisplayOutputCleanupStore {
    private final FailedDisplayOutputCleanupMapper mapper;

    MyBatisFailedDisplayOutputCleanupStore(FailedDisplayOutputCleanupMapper mapper) {
        this.mapper = mapper;
    }

    @Override long latestCandidateId(LocalDateTime cutoff) { return mapper.latestCandidateId(cutoff); }
    @Override List<Long> candidatesAfter(long afterId, long upperId, LocalDateTime cutoff, int limit) {
        return mapper.candidatesAfter(afterId, upperId, cutoff, limit);
    }
    @Override MediaProcessingJobEntity jobForUpdate(Long jobId) { return mapper.jobForUpdate(jobId); }
    @Override MediaObjectEntity media(Long mediaId) { return mapper.media(mediaId); }
    @Override MediaObjectEntity original(String inputKey) { return mapper.original(inputKey); }
    @Override boolean cleaned(Long jobId, int attemptNo) { return mapper.cleaned(jobId, attemptNo) > 0; }
    @Override void markCleaned(Long jobId, int attemptNo) {
        if (mapper.markCleaned(jobId, attemptNo) != 1) {
            throw new IllegalStateException("Failed display cleanup marker was not persisted.");
        }
    }
}
