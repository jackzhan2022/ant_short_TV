package com.antshorttv.storage;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailedDisplayOutputCleanupService {
    private final FailedDisplayOutputCleanupStore store;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final ImageDisplayRenditionPlanner planner;

    public FailedDisplayOutputCleanupService(
        FailedDisplayOutputCleanupStore store,
        ObjectStorageService storage,
        ObjectStorageKeyFactory keys,
        ImageDisplayRenditionPlanner planner
    ) {
        this.store = store;
        this.storage = storage;
        this.keys = keys;
        this.planner = planner;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean clean(Long jobId, LocalDateTime cutoff) {
        if (jobId == null || cutoff == null) return false;
        // Keep the callback/retry job lock through deletion; extra media locks invert retry's lock order.
        MediaProcessingJobEntity job = store.jobForUpdate(jobId);
        if (job == null || !"FAILED".equals(job.status)
            || !"DISPLAY_IMAGE_SLIM".equals(job.operation) || job.completedAt == null
            || job.completedAt.isAfter(cutoff) || job.attemptNo == null || job.attemptNo < 1
            || store.cleaned(job.id, job.attemptNo)) {
            return false;
        }
        MediaObjectEntity display = store.media(job.mediaObjectId);
        if (display == null || !"FAILED".equals(display.status)
            || !"DISPLAY_IMAGE_SLIM".equals(display.renditionType)
            || !Objects.equals(job.mediaObjectId, display.id)
            || !Objects.equals(job.outputKey, display.objectKey)
            || !Objects.equals(job.tenantId, display.tenantId)
            || !Objects.equals(job.projectId, display.projectId) || blank(display.storageClass)) {
            return false;
        }
        MediaObjectEntity original = store.original(job.inputKey);
        if (!validOriginal(original, display) || !Objects.equals(job.inputKey, original.objectKey)) {
            return false;
        }
        try {
            ImageDisplayRenditionPlan plan = planner.plan(original.objectKey, original.mimeType);
            if (!plan.objectKey().equals(display.objectKey) || !plan.mimeType().equals(display.mimeType)) {
                return false;
            }
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        storage.delete(job.outputKey);
        store.markCleaned(job.id, job.attemptNo);
        return true;
    }

    private boolean validOriginal(MediaObjectEntity original, MediaObjectEntity display) {
        if (original == null || !"ORIGINAL".equals(original.renditionType)
            || !"READY".equals(original.status) || !original.identity().equals(display.identity())
            || original.fileSize == null || original.fileSize <= 0 || blank(original.etag)
            || original.width == null || original.width <= 0 || original.height == null || original.height <= 0
            || blank(original.storageClass) || blank(original.mimeType)
            || original.tenantId == null || original.tenantId < 0
            || original.projectId != null && original.projectId <= 0
            || original.assetId == null || original.assetId <= 0
            || blank(original.assetType) || blank(original.versionId)) {
            return false;
        }
        try {
            String input = keys.objectKey(original.objectKey);
            if (input.contains("/derived/") || input.contains("/uploads/")) return false;
            String prefix = "materials/" + original.tenantId + "/"
                + (original.projectId == null ? "" : original.projectId + "/");
            // Logical registry versions can differ from the producer's immutable key-directory version.
            return input.matches(Pattern.quote(prefix) + "[^/]+/[0-9]{6}/"
                + Pattern.quote(original.assetId.toString()) + "/[^/]+/(?:cover/)?original\\.[a-zA-Z0-9]{1,10}");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
