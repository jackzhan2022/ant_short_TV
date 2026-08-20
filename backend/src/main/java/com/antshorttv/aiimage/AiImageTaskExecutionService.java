package com.antshorttv.aiimage;

import com.antshorttv.ai.AiContext;
import com.antshorttv.ai.AiGateway;
import com.antshorttv.ai.AiImageRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AiImageTaskExecutionService {
    private final AiImageTaskMapper taskMapper;
    private final AiImageResultMapper resultMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiImageStorageService storageService;
    private final AiGateway aiGateway;

    public AiImageTaskExecutionService(
        AiImageTaskMapper taskMapper,
        AiImageResultMapper resultMapper,
        AiCallLogMapper aiCallLogMapper,
        AiImageStorageService storageService,
        AiGateway aiGateway
    ) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.storageService = storageService;
        this.aiGateway = aiGateway;
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

        try {
            Thread.sleep(200);
            if (isCanceled(task.getId())) {
                return;
            }
            aiGateway.image(
                new AiContext(task.getTenantId(), task.getCreatedBy(), task.getProjectId(), task.getId(), task.getModelId(), task.getTaskType(), null),
                new AiImageRequest(task.getPrompt(), task.getNegativePrompt(), null, task.getAspectRatio(), task.getImageCount(), ReferenceImagesCodec.decode(task.getReferenceImages()))
            );
            for (int index = 1; index <= task.getImageCount(); index++) {
                createResult(task, index);
            }
            if (isCanceled(task.getId())) {
                return;
            }
            task.setStatus(AiImageTaskStatus.SUCCESS.name());
        } catch (Exception exception) {
            task.setStatus(AiImageTaskStatus.FAILED.name());
            task.setErrorMessage(exception.getMessage());
        }

        LocalDateTime completed = LocalDateTime.now();
        AiCallLogEntity log = aiCallLogMapper.selectOne(new LambdaQueryWrapper<AiCallLogEntity>()
            .eq(AiCallLogEntity::getTenantId, task.getTenantId())
            .eq(AiCallLogEntity::getUserId, task.getCreatedBy())
            .eq(AiCallLogEntity::getBusinessScene, task.getTaskType())
            .orderByDesc(AiCallLogEntity::getId)
            .last("limit 1"));
        task.setAiCallLogId(log == null ? null : log.getId());
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
