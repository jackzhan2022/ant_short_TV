package com.antshorttv.video;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record AiVideoTaskResponse(
    Long id,
    Long executionId,
    Long projectId,
    Long storyboardId,
    Long modelId,
    String providerCode,
    String model,
    String prompt,
    String negativePrompt,
    String firstFrameUrl,
    Integer durationSeconds,
    String aspectRatio,
    String resolution,
    String motionStrength,
    String cameraMovement,
    String externalTaskId,
    String externalStatus,
    String status,
    String errorMessage,
    String executionPhase,
    Boolean retryable,
    LocalDateTime submittedAt,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    List<AiVideoResultResponse> results
) {
    static AiVideoTaskResponse from(AiVideoTaskEntity entity, List<AiVideoResultEntity> results) {
        return new AiVideoTaskResponse(
            entity.id,
            entity.executionId,
            entity.projectId,
            entity.storyboardId,
            entity.modelId,
            entity.providerCode,
            entity.model,
            entity.prompt,
            entity.negativePrompt,
            entity.firstFrameUrl,
            entity.durationSeconds,
            entity.aspectRatio,
            entity.resolution,
            entity.motionStrength,
            entity.cameraMovement,
            entity.externalTaskId,
            entity.externalStatus,
            entity.status,
            entity.errorMessage,
            entity.executionPhase,
            entity.retryable,
            entity.submittedAt,
            entity.startedAt,
            entity.completedAt,
            entity.createdAt,
            results.stream().map(AiVideoResultResponse::from).toList()
        );
    }
}

record AiVideoResultResponse(
    Long id,
    Long taskId,
    Long storyboardId,
    String videoUrl,
    String storagePath,
    String coverUrl,
    BigDecimal durationSeconds,
    Integer width,
    Integer height,
    Long fileSize,
    String format,
    Long materialId,
    Boolean isSelected,
    String status,
    LocalDateTime createdAt
) {
    static AiVideoResultResponse from(AiVideoResultEntity entity) {
        return new AiVideoResultResponse(
            entity.id,
            entity.taskId,
            entity.storyboardId,
            entity.videoUrl,
            entity.storagePath,
            entity.coverUrl,
            entity.durationSeconds,
            entity.width,
            entity.height,
            entity.fileSize,
            entity.format,
            entity.materialId,
            entity.isSelected,
            entity.status,
            entity.createdAt
        );
    }
}
