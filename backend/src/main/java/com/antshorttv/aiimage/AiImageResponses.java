package com.antshorttv.aiimage;

import java.time.LocalDateTime;
import java.util.List;

record AiImageTaskResponse(
    Long id,
    Long projectId,
    String taskType,
    String targetType,
    Long targetId,
    Long serviceConfigId,
    Long modelId,
    String providerCode,
    String model,
    String prompt,
    String negativePrompt,
    List<String> referenceImages,
    String aspectRatio,
    Integer imageCount,
    String style,
    String quality,
    String seed,
    String status,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Long createdBy,
    LocalDateTime createdAt,
    List<AiImageResultResponse> results
) {
    static AiImageTaskResponse from(AiImageTaskEntity entity, List<AiImageResultEntity> results) {
        return new AiImageTaskResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getTaskType(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getServiceConfigId(),
            entity.getModelId(),
            entity.getProviderCode(),
            entity.getModel(),
            entity.getPrompt(),
            entity.getNegativePrompt(),
            ReferenceImagesCodec.decode(entity.getReferenceImages()),
            entity.getAspectRatio(),
            entity.getImageCount(),
            entity.getStyle(),
            entity.getQuality(),
            entity.getSeed(),
            entity.getStatus(),
            entity.getErrorMessage(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getCreatedBy(),
            entity.getCreatedAt(),
            results.stream().map(AiImageResultResponse::from).toList()
        );
    }
}

record AiImageResultResponse(
    Long id,
    Long taskId,
    String targetType,
    Long targetId,
    String imageUrl,
    String thumbnailUrl,
    Integer width,
    Integer height,
    Long fileSize,
    Long materialId,
    Boolean selected,
    String status,
    LocalDateTime createdAt
) {
    static AiImageResultResponse from(AiImageResultEntity entity) {
        return new AiImageResultResponse(
            entity.getId(),
            entity.getTaskId(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getImageUrl(),
            entity.getThumbnailUrl(),
            entity.getWidth(),
            entity.getHeight(),
            entity.getFileSize(),
            entity.getMaterialId(),
            entity.getIsSelected(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
