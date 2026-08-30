package com.antshorttv.video;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record VideoDecompositionBatchResponse(
    Long id,
    Long tenantId,
    Long projectId,
    String name,
    Long modelId,
    String status,
    Integer totalEpisodes,
    Integer completedEpisodes,
    Integer failedEpisodes,
    Integer succeededEpisodes,
    Integer processingEpisodes,
    Integer pendingEpisodes,
    Integer percentage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<VideoDecompositionEpisodeResponse> episodes
) {
    static VideoDecompositionBatchResponse from(
        VideoDecompositionBatchEntity batch,
        List<VideoDecompositionEpisodeEntity> episodes
    ) {
        return from(batch, episodes, VideoDecompositionEpisodeResponse::from, ignored -> null);
    }

    static VideoDecompositionBatchResponse from(
        VideoDecompositionBatchEntity batch,
        List<VideoDecompositionEpisodeEntity> episodes,
        java.util.function.Function<VideoDecompositionEpisodeEntity, VideoDecompositionEpisodeResponse> episodeMapper,
        java.util.function.Function<VideoDecompositionEpisodeEntity, Integer> progressMapper
    ) {
        VideoDecompositionBatchProgress progress = VideoDecompositionBatchProgress.fromEpisodes(episodes, progressMapper);
        return new VideoDecompositionBatchResponse(
            batch.getId(),
            batch.getTenantId(),
            batch.getProjectId(),
            batch.getName(),
            batch.getModelId(),
            batch.getStatus(),
            batch.getTotalEpisodes(),
            batch.getCompletedEpisodes(),
            batch.getFailedEpisodes(),
            progress.succeeded(),
            progress.processing(),
            progress.pending(),
            progress.percentage(),
            batch.getCreatedAt(),
            batch.getUpdatedAt(),
            episodes.stream().map(episodeMapper).toList()
        );
    }
}

record VideoDecompositionEpisodeResponse(
    Long id,
    Long executionId,
    Long batchId,
    Long projectId,
    Integer episodeNo,
    String sourceFileName,
    String storagePath,
    String mimeType,
    Long fileSize,
    BigDecimal durationSeconds,
    String status,
    Integer analysisVersion,
    String draftStatus,
    Integer draftVersion,
    Long confirmedScriptVersionId,
    String errorCode,
    String errorMessage,
    String executionPhase,
    Integer percentage,
    Boolean retryable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    static VideoDecompositionEpisodeResponse from(VideoDecompositionEpisodeEntity entity) {
        return from(entity, null);
    }

    static VideoDecompositionEpisodeResponse from(VideoDecompositionEpisodeEntity entity, Integer persistedProgress) {
        return new VideoDecompositionEpisodeResponse(
            entity.getId(),
            entity.getExecutionId(),
            entity.getBatchId(),
            entity.getProjectId(),
            entity.getEpisodeNo(),
            entity.getSourceFileName(),
            entity.getStoragePath(),
            entity.getMimeType(),
            entity.getFileSize(),
            entity.getDurationSeconds(),
            entity.getStatus(),
            entity.getAnalysisVersion(),
            entity.getDraftStatus(),
            entity.getDraftVersion(),
            entity.getConfirmedScriptVersionId(),
            entity.getErrorCode(),
            entity.getErrorMessage(),
            entity.getExecutionPhase(),
            VideoDecompositionBatchProgress.episodePercentage(entity.getStatus(), persistedProgress),
            entity.getRetryable(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

record VideoDecompositionUploadResponse(
    String fileName,
    String storagePath,
    String mimeType,
    Long fileSize,
    BigDecimal durationSeconds
) {
}

record VideoDecompositionEpisodeDetailResponse(
    VideoDecompositionEpisodeResponse episode,
    String screenplayContent,
    String formatVersion,
    String draftContent,
    Long currentScriptVersionId,
    String rawResponse,
    String normalizedJson,
    List<VideoDecompositionAttemptResponse> attempts
) {
}

record VideoDecompositionBatchScreenplaysResponse(
    Long batchId,
    String batchName,
    String status,
    Integer percentage,
    Integer totalEpisodes,
    Integer succeededEpisodes,
    Integer failedEpisodes,
    Integer processingEpisodes,
    Integer pendingEpisodes,
    List<VideoDecompositionScreenplayEpisodeResponse> episodes
) {
}

record VideoDecompositionScreenplayEpisodeResponse(
    VideoDecompositionEpisodeResponse episode,
    String screenplayContent,
    String formatVersion
) {
}

record VideoDecompositionAttemptResponse(
    Long id,
    Integer attemptNo,
    String phase,
    String status,
    String providerRequestId,
    Long aiCallLogId,
    String idempotencyKey,
    Boolean retryable,
    String errorCode,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
    static VideoDecompositionAttemptResponse from(VideoDecompositionAttemptEntity entity) {
        return new VideoDecompositionAttemptResponse(
            entity.getId(),
            entity.getAttemptNo(),
            entity.getPhase(),
            entity.getStatus(),
            entity.getProviderRequestId(),
            entity.getAiCallLogId(),
            entity.getIdempotencyKey(),
            entity.getRetryable(),
            entity.getErrorCode(),
            entity.getErrorMessage(),
            entity.getStartedAt(),
            entity.getFinishedAt()
        );
    }
}
