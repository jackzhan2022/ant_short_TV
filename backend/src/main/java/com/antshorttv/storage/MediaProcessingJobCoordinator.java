package com.antshorttv.storage;

import com.qcloud.cos.model.ciModel.job.MediaJobObject;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaProcessingJobCoordinator {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED");
    private static final Set<String> IMAGE_FORMATS = Set.of("jpg", "jpeg", "png", "gif");
    private static final long STALE_SUBMISSION_MINUTES = 5;
    private final MediaProcessingJobStore jobs;
    private final MediaObjectStore mediaObjects;
    private final MediaObjectRegistry mediaObjectRegistry;

    public MediaProcessingJobCoordinator(
        MediaProcessingJobStore jobs,
        MediaObjectStore mediaObjects,
        MediaObjectRegistry mediaObjectRegistry
    ) {
        this.jobs = jobs;
        this.mediaObjects = mediaObjects;
        this.mediaObjectRegistry = mediaObjectRegistry;
    }

    @Transactional
    public PreparedMediaProcessingJob prepareImageDisplay(
        SubmitImageDisplayJob command,
        ImageDisplayRenditionPlan plan,
        String callbackTokenHash
    ) {
        RegisteredMediaObject rendition = mediaObjectRegistry.registerPendingRendition(
            command.identity(), "DISPLAY_IMAGE_SLIM", plan.objectKey(), plan.mimeType(),
            command.storageClass()
        );
        SubmitMediaProcessingJob submission = new SubmitMediaProcessingJob(
            command.identity().tenantId(), command.identity().projectId(), rendition.id(),
            "DISPLAY_IMAGE_SLIM", command.inputKey(), plan.objectKey(), plan.processRule(),
            command.correlationData()
        );
        LocalDateTime now = LocalDateTime.now();
        MediaProcessingJobEntity existing = jobs.find(plan.objectKey(), "DISPLAY_IMAGE_SLIM");
        if (existing != null && !"FAILED".equals(existing.status)
            && !staleSubmission(existing, now)) {
            return new PreparedMediaProcessingJob(submission, existing.callbackTokenHash, false,
                response(existing));
        }

        MediaProcessingJobEntity entity = existing == null ? new MediaProcessingJobEntity() : existing;
        entity.tenantId = submission.tenantId();
        entity.projectId = submission.projectId();
        entity.mediaObjectId = submission.mediaObjectId();
        entity.providerJobId = null;
        entity.queueId = null;
        entity.operation = submission.operation();
        entity.inputKey = submission.inputKey();
        entity.outputKey = submission.outputKey();
        entity.callbackTokenHash = callbackTokenHash;
        entity.correlationData = submission.correlationData();
        entity.status = "SUBMITTING";
        entity.attemptNo = existing == null || existing.attemptNo == null ? 1 : existing.attemptNo + 1;
        entity.errorCode = null;
        entity.errorMessage = null;
        entity.submittedAt = null;
        entity.completedAt = null;
        entity.createdAt = existing == null ? now : existing.createdAt;
        entity.updatedAt = now;
        if (existing == null) jobs.insert(entity); else jobs.update(entity);
        return new PreparedMediaProcessingJob(submission, callbackTokenHash, true, response(entity));
    }

    @Transactional
    public SubmittedMediaProcessingJob finalizeSubmission(
        PreparedMediaProcessingJob prepared,
        MediaJobObject detail
    ) {
        if (detail == null || blank(detail.getJobId())) {
            throw new IllegalStateException("万象图片处理任务未返回 JobId。");
        }
        MediaProcessingJobEntity entity = current(prepared);
        if (!blank(entity.providerJobId) && !same(entity.providerJobId, detail.getJobId())) {
            throw new IllegalArgumentException("万象提交结果与处理任务不匹配。");
        }
        if (TERMINAL.contains(entity.status)) return response(entity);
        LocalDateTime now = LocalDateTime.now();
        entity.providerJobId = detail.getJobId();
        entity.queueId = detail.getQueueId();
        entity.status = "Running".equalsIgnoreCase(detail.getState()) ? "RUNNING" : "SUBMITTED";
        entity.submittedAt = now;
        entity.updatedAt = now;
        jobs.update(entity);
        return response(entity);
    }

    @Transactional
    public void failSubmission(PreparedMediaProcessingJob prepared, String message) {
        MediaProcessingJobEntity entity = current(prepared);
        if (TERMINAL.contains(entity.status)) return;
        LocalDateTime now = LocalDateTime.now();
        failed(entity, "SUBMIT_FAILED", message, now);
        entity.updatedAt = now;
        jobs.update(entity);
    }

    @Transactional
    public void handleCallback(String tokenHash, TencentCiTaskCallback callback) {
        MediaProcessingJobEntity entity = jobs.findByTokenHash(tokenHash);
        if (entity == null) throw new IllegalArgumentException("万象回调令牌无效。");
        if (TERMINAL.contains(entity.status)) return;
        TencentCiJobDetail detail = single(callback);
        if (blank(detail.jobId())
            || !blank(entity.providerJobId) && !same(entity.providerJobId, detail.jobId())
            || detail.input() == null || !same(entity.inputKey, detail.input().object())
            || detail.operation() == null || detail.operation().output() == null
            || !same(entity.outputKey, detail.operation().output().object())
            || !same(entity.correlationData, detail.operation().userData())) {
            throw new IllegalArgumentException("万象回调与处理任务不匹配。");
        }
        if (blank(entity.providerJobId)) entity.providerJobId = detail.jobId();

        LocalDateTime now = LocalDateTime.now();
        if ("Success".equalsIgnoreCase(detail.state()) && "Success".equalsIgnoreCase(detail.code())) {
            TencentCiProcessResult result = detail.operation().picProcessResult() == null
                ? null : detail.operation().picProcessResult().processResult();
            if (!valid(result)) {
                failed(entity, "INVALID_RESULT_METADATA",
                    "万象成功回调图片元数据不完整或格式无效。", now);
            } else {
                entity.status = "SUCCEEDED";
                entity.completedAt = now;
                mediaObjects.ready(
                    entity.mediaObjectId, result.size(), result.eTag(), mimeType(result.format()),
                    result.width(), result.height()
                );
            }
        } else {
            failed(entity, detail.code(), detail.message(), now);
        }
        entity.updatedAt = now;
        jobs.update(entity);
    }

    private MediaProcessingJobEntity current(PreparedMediaProcessingJob prepared) {
        MediaProcessingJobEntity entity = jobs.find(
            prepared.command().outputKey(), prepared.command().operation()
        );
        if (entity == null || !same(entity.callbackTokenHash, prepared.callbackTokenHash())) {
            throw new IllegalStateException("万象处理任务提交尝试已失效。");
        }
        return entity;
    }

    private boolean staleSubmission(MediaProcessingJobEntity entity, LocalDateTime now) {
        return Set.of("SUBMITTING", "PENDING").contains(entity.status)
            && blank(entity.providerJobId)
            && (entity.updatedAt == null
                || entity.updatedAt.isBefore(now.minusMinutes(STALE_SUBMISSION_MINUTES)));
    }

    private TencentCiJobDetail single(TencentCiTaskCallback callback) {
        if (callback == null || !"TaskFinish".equals(callback.eventName())
            || callback.jobsDetail() == null || callback.jobsDetail().size() != 1) {
            throw new IllegalArgumentException("万象回调内容不合法。");
        }
        return callback.jobsDetail().get(0);
    }

    private boolean valid(TencentCiProcessResult result) {
        return result != null && result.size() > 0 && result.width() > 0 && result.height() > 0
            && !blank(result.eTag()) && !blank(result.format())
            && IMAGE_FORMATS.contains(result.format().toLowerCase(Locale.ROOT));
    }

    private String mimeType(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> throw new IllegalArgumentException("万象成功回调图片格式无效。");
        };
    }

    private void failed(
        MediaProcessingJobEntity entity,
        String code,
        String message,
        LocalDateTime now
    ) {
        entity.status = "FAILED";
        entity.errorCode = code;
        entity.errorMessage = message;
        entity.completedAt = now;
        mediaObjects.failed(entity.mediaObjectId, message);
    }

    private SubmittedMediaProcessingJob response(MediaProcessingJobEntity entity) {
        return new SubmittedMediaProcessingJob(entity.providerJobId, entity.status, entity.outputKey);
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

record PreparedMediaProcessingJob(
    SubmitMediaProcessingJob command,
    String callbackTokenHash,
    boolean shouldSubmit,
    SubmittedMediaProcessingJob current
) {
}
