package com.antshorttv.storage;

import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.common.MediaInputObject;
import com.qcloud.cos.model.ciModel.common.MediaOutputObject;
import com.qcloud.cos.model.ciModel.job.MediaJobObject;
import com.qcloud.cos.model.ciModel.job.MediaJobOperation;
import com.qcloud.cos.model.ciModel.job.MediaJobResponse;
import com.qcloud.cos.model.ciModel.job.MediaJobsRequest;
import com.qcloud.cos.model.ciModel.job.MediaPicProcessTemplateObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloudInfiniteProcessingService {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED");
    private final COS cos;
    private final ObjectStorageProperties properties;
    private final ObjectStorageKeyFactory keys;
    private final MediaProcessingJobStore jobs;
    private final MediaObjectStore mediaObjects;
    private final MediaObjectRegistry mediaObjectRegistry;
    private final ImageDisplayRenditionPlanner imageRenditions;
    private final SecureRandom random = new SecureRandom();

    public CloudInfiniteProcessingService(
        COS cos,
        ObjectStorageProperties properties,
        ObjectStorageKeyFactory keys,
        MediaProcessingJobStore jobs,
        MediaObjectStore mediaObjects,
        MediaObjectRegistry mediaObjectRegistry,
        ImageDisplayRenditionPlanner imageRenditions
    ) {
        this.cos = cos;
        this.properties = properties;
        this.keys = keys;
        this.jobs = jobs;
        this.mediaObjects = mediaObjects;
        this.mediaObjectRegistry = mediaObjectRegistry;
        this.imageRenditions = imageRenditions;
    }

    @Transactional
    public SubmittedMediaProcessingJob submitImageDisplay(SubmitImageDisplayJob command) {
        if (command == null || command.identity() == null || blank(command.inputKey())
            || blank(command.sourceMimeType()) || blank(command.storageClass())
            || blank(command.correlationData())) {
            throw new IllegalArgumentException("万象图片展示任务参数不完整。");
        }
        String inputKey = keys.objectKey(command.inputKey());
        ImageDisplayRenditionPlan plan = imageRenditions.plan(inputKey, command.sourceMimeType());
        RegisteredMediaObject rendition = mediaObjectRegistry.registerPendingRendition(
            command.identity(),
            "DISPLAY_IMAGE_SLIM",
            plan.objectKey(),
            plan.mimeType(),
            command.storageClass()
        );
        return submit(new SubmitMediaProcessingJob(
            command.identity().tenantId(),
            command.identity().projectId(),
            rendition.id(),
            "DISPLAY_IMAGE_SLIM",
            inputKey,
            plan.objectKey(),
            plan.processRule(),
            command.correlationData()
        ));
    }

    @Transactional
    public SubmittedMediaProcessingJob submit(SubmitMediaProcessingJob command) {
        validate(command);
        String inputKey = keys.objectKey(command.inputKey());
        String outputKey = keys.objectKey(command.outputKey());
        MediaProcessingJobEntity existing = jobs.find(outputKey, command.operation());
        if (existing != null && !"FAILED".equals(existing.status)) {
            return response(existing);
        }

        String token = token();
        LocalDateTime now = LocalDateTime.now();
        MediaProcessingJobEntity entity = existing == null ? new MediaProcessingJobEntity() : existing;
        entity.tenantId = command.tenantId();
        entity.projectId = command.projectId();
        entity.mediaObjectId = command.mediaObjectId();
        entity.operation = command.operation();
        entity.inputKey = inputKey;
        entity.outputKey = outputKey;
        entity.callbackTokenHash = hash(token);
        entity.correlationData = command.correlationData();
        entity.status = "PENDING";
        entity.attemptNo = existing == null || existing.attemptNo == null ? 1 : existing.attemptNo + 1;
        entity.errorCode = null;
        entity.errorMessage = null;
        entity.completedAt = null;
        entity.createdAt = existing == null ? now : existing.createdAt;
        entity.updatedAt = now;
        if (existing == null) jobs.insert(entity); else jobs.update(entity);

        MediaJobObject detail = submitToTencent(command, inputKey, outputKey, token);
        if (detail == null || detail.getJobId() == null || detail.getJobId().isBlank()) {
            throw new IllegalStateException("万象图片处理任务未返回 JobId。");
        }
        entity.providerJobId = detail.getJobId();
        entity.queueId = detail.getQueueId();
        entity.status = normalizeSubmittedState(detail.getState());
        entity.submittedAt = now;
        entity.updatedAt = now;
        jobs.update(entity);
        return response(entity);
    }

    @Transactional
    public void handleCallback(String token, TencentCiTaskCallback callback) {
        MediaProcessingJobEntity entity = jobs.findByTokenHash(hash(token));
        if (entity == null) throw new IllegalArgumentException("万象回调令牌无效。");
        if (TERMINAL.contains(entity.status)) return;
        TencentCiJobDetail detail = single(callback);
        if (!same(entity.providerJobId, detail.jobId())
            || detail.input() == null || !same(entity.inputKey, detail.input().object())
            || detail.operation() == null || detail.operation().output() == null
            || !same(entity.outputKey, detail.operation().output().object())
            || !same(entity.correlationData, detail.operation().userData())) {
            throw new IllegalArgumentException("万象回调与处理任务不匹配。");
        }

        LocalDateTime now = LocalDateTime.now();
        if ("Success".equalsIgnoreCase(detail.state()) && "Success".equalsIgnoreCase(detail.code())) {
            TencentCiProcessResult result = detail.operation().picProcessResult() == null
                ? null : detail.operation().picProcessResult().processResult();
            if (!valid(result)) {
                failed(
                    entity,
                    "INVALID_RESULT_METADATA",
                    "万象成功回调图片元数据不完整或格式无效。",
                    now
                );
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

    private MediaJobObject submitToTencent(
        SubmitMediaProcessingJob command,
        String inputKey,
        String outputKey,
        String token
    ) {
        MediaInputObject input = new MediaInputObject();
        input.setObject(inputKey);
        MediaOutputObject output = new MediaOutputObject();
        output.setBucket(properties.getBucket());
        output.setRegion(properties.getRegion());
        output.setObject(outputKey);
        MediaPicProcessTemplateObject process = new MediaPicProcessTemplateObject();
        process.setIsPicInfo("true");
        process.setProcessRule(command.processRule());
        MediaJobOperation operation = new MediaJobOperation();
        operation.setPicProcess(process);
        operation.setOutput(output);
        operation.setUserData(command.correlationData());
        MediaJobsRequest request = new MediaJobsRequest();
        request.setBucketName(properties.getBucket());
        request.setTag("PicProcess");
        request.setInput(input);
        request.setOperation(operation);
        request.setCallBack(callbackBase() + "/" + token);
        request.setCallBackFormat("JSON");
        request.setCallBackType("Url");
        MediaJobResponse response = cos.createPicProcessJob(request);
        return response == null ? null : response.getJobsDetail();
    }

    private void validate(SubmitMediaProcessingJob command) {
        if (command == null || command.tenantId() == null || command.mediaObjectId() == null
            || blank(command.operation()) || blank(command.inputKey()) || blank(command.outputKey())
            || blank(command.processRule()) || blank(command.correlationData())) {
            throw new IllegalArgumentException("万象处理任务参数不完整。");
        }
        if (command.inputKey().contains("/derived/") || !command.outputKey().contains("/derived/")) {
            throw new IllegalArgumentException("万象处理仅允许从原图生成固定派生对象。");
        }
    }

    private TencentCiJobDetail single(TencentCiTaskCallback callback) {
        if (callback == null || !"TaskFinish".equals(callback.eventName())
            || callback.jobsDetail() == null || callback.jobsDetail().size() != 1) {
            throw new IllegalArgumentException("万象回调内容不合法。");
        }
        return callback.jobsDetail().get(0);
    }

    private String callbackBase() {
        String value = properties.getCiCallbackUrl();
        if (blank(value)) throw new IllegalStateException("object-storage.ci-callback-url 未配置。");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        if (blank(value)) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算回调令牌摘要。", exception);
        }
    }

    private SubmittedMediaProcessingJob response(MediaProcessingJobEntity entity) {
        return new SubmittedMediaProcessingJob(entity.providerJobId, entity.status, entity.outputKey);
    }
    private String normalizeSubmittedState(String state) {
        return "Running".equalsIgnoreCase(state) ? "RUNNING" : "SUBMITTED";
    }
    private String mimeType(String format) {
        return switch (format.toLowerCase(java.util.Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> throw new IllegalArgumentException("万象成功回调图片格式无效。");
        };
    }
    private boolean valid(TencentCiProcessResult result) {
        return result != null && result.size() > 0 && result.width() > 0 && result.height() > 0
            && !blank(result.eTag()) && !blank(result.format())
            && Set.of("jpg", "jpeg", "png", "gif")
                .contains(result.format().toLowerCase(java.util.Locale.ROOT));
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
    private boolean same(String left, String right) { return left != null && left.equals(right); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
