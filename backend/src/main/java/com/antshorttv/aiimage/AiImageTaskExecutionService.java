package com.antshorttv.aiimage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AiImageTaskExecutionService {
    private final AiImageTaskMapper taskMapper;
    private final AiImageResultMapper resultMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiImageStorageService storageService;

    public AiImageTaskExecutionService(
        AiImageTaskMapper taskMapper,
        AiImageResultMapper resultMapper,
        AiCallLogMapper aiCallLogMapper,
        AiImageStorageService storageService
    ) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.storageService = storageService;
    }

    @Async
    public void execute(Long taskId) {
        AiImageTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !AiImageTaskStatus.PENDING.name().equals(task.getStatus())) {
            return;
        }
        LocalDateTime started = LocalDateTime.now();
        task.setStatus(AiImageTaskStatus.RUNNING.name());
        task.setStartedAt(started);
        task.setUpdatedAt(started);
        taskMapper.updateById(task);

        String status = "SUCCESS";
        String errorMessage = null;
        try {
            Thread.sleep(200);
            if (isCanceled(task.getId())) {
                return;
            }
            for (int index = 1; index <= task.getImageCount(); index++) {
                createResult(task, index);
            }
            if (isCanceled(task.getId())) {
                return;
            }
            task.setStatus(AiImageTaskStatus.SUCCESS.name());
        } catch (Exception exception) {
            status = "FAILED";
            errorMessage = exception.getMessage();
            task.setStatus(AiImageTaskStatus.FAILED.name());
            task.setErrorMessage(errorMessage);
        }

        LocalDateTime completed = LocalDateTime.now();
        AiCallLogEntity log = new AiCallLogEntity();
        log.setTenantId(task.getTenantId());
        log.setUserId(task.getCreatedBy());
        log.setServiceConfigId(task.getServiceConfigId());
        log.setProvider(task.getProviderCode());
        log.setServiceType("IMAGE");
        log.setModel(task.getModel());
        log.setBusinessScene(task.getTaskType());
        log.setRequestSummary(task.getPrompt());
        log.setResponseSummary("generated=%d".formatted(resultMapper.selectActiveByTask(task.getId()).size()));
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setDurationMs(Duration.between(started, completed).toMillis());
        log.setCreatedAt(completed);
        aiCallLogMapper.insert(log);

        task.setAiCallLogId(log.getId());
        task.setCompletedAt(completed);
        task.setUpdatedAt(completed);
        taskMapper.updateById(task);
    }

    private boolean isCanceled(Long taskId) {
        AiImageTaskEntity latest = taskMapper.selectById(taskId);
        return latest == null || AiImageTaskStatus.CANCELED.name().equals(latest.getStatus());
    }

    private void createResult(AiImageTaskEntity task, int index) {
        AiImageResultEntity result = new AiImageResultEntity();
        result.setTenantId(task.getTenantId());
        result.setProjectId(task.getProjectId());
        result.setTaskId(task.getId());
        result.setTargetType(task.getTargetType());
        result.setTargetId(task.getTargetId());
        result.setImageUrl("");
        result.setThumbnailUrl("");
        result.setIsSelected(false);
        result.setStatus(AiImageResultStatus.ACTIVE.name());
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(result.getCreatedAt());
        resultMapper.insert(result);

        StoredImage storedImage = storageService.createPlaceholder(task, result.getId(), index);
        String url = "/api/projects/%d/ai-image-results/%d/download".formatted(task.getProjectId(), result.getId());
        result.setImageUrl(url);
        result.setThumbnailUrl(url);
        result.setStoragePath(storedImage.storagePath());
        result.setWidth(storedImage.width());
        result.setHeight(storedImage.height());
        result.setFileSize(storedImage.fileSize());
        result.setUpdatedAt(LocalDateTime.now());
        resultMapper.updateById(result);
    }
}
