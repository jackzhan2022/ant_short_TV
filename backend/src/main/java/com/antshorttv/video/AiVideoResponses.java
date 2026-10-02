package com.antshorttv.video;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

record AiVideoTaskResponse(
    Long id,
    Long executionId,
    Long projectId,
    Long storyboardId,
    Long modelId,
    String providerCode,
    String model,
    String prompt,
    String compiledPrompt,
    String negativePrompt,
    String firstFrameUrl,
    Integer durationSeconds,
    String aspectRatio,
    String resolution,
    Boolean generateAudio,
    Boolean watermark,
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
    JsonNode referenceDiagnostics,
    List<AiVideoResultResponse> results,
    long resultCount
) {
    static AiVideoTaskResponse from(AiVideoTaskEntity entity, List<AiVideoResultEntity> results) {
        return from(entity, results, results.size(), false);
    }

    static AiVideoTaskResponse from(AiVideoTaskEntity entity, List<AiVideoResultEntity> results, long resultCount) {
        return from(entity, results, resultCount, true);
    }

    private static AiVideoTaskResponse from(AiVideoTaskEntity entity, List<AiVideoResultEntity> results, long resultCount, boolean browser) {
        return new AiVideoTaskResponse(
            entity.id,
            entity.executionId,
            entity.projectId,
            entity.storyboardId,
            entity.modelId,
            entity.providerCode,
            entity.model,
            entity.prompt,
            entity.compiledPrompt,
            entity.negativePrompt,
            entity.firstFrameUrl,
            entity.durationSeconds,
            entity.aspectRatio,
            entity.resolution,
            entity.generateAudio,
            entity.watermark,
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
            referenceDiagnostics(entity.requestSnapshotJson),
            results.stream().map(r -> browser ? AiVideoResultResponse.fromBrowser(r) : AiVideoResultResponse.from(r)).toList(), resultCount
        );
    }

    private static JsonNode referenceDiagnostics(String snapshotJson) {
        ObjectMapper mapper = new ObjectMapper();
        var result = mapper.createObjectNode();
        if (snapshotJson == null || snapshotJson.isBlank()) return result;
        try {
            JsonNode snapshot = mapper.readTree(snapshotJson);
            result.set("limits", snapshot.path("referenceLimits").deepCopy());
            result.set("kept", snapshot.path("references").deepCopy());
            result.set("omitted", snapshot.path("omittedReferences").deepCopy());
        } catch (Exception ignored) {
            // Older tasks without a valid snapshot expose an empty diagnostic object.
        }
        return result;
    }
}

record AiVideoResultResponse(
    Long id,
    Long taskId,
    Long storyboardId,
    String videoUrl,
    String storagePath,
    String referenceUrl,
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
        return from(entity, false);
    }

    static AiVideoResultResponse fromBrowser(AiVideoResultEntity entity) {
        return from(entity, true);
    }

    private static AiVideoResultResponse from(AiVideoResultEntity entity, boolean browser) {
        return new AiVideoResultResponse(
            entity.id,
            entity.taskId,
            entity.storyboardId,
            browser ? "/api/projects/" + entity.projectId + "/ai-video-results/" + entity.id + "/playback" : entity.videoUrl,
            entity.storagePath,
            entity.videoUrl,
            browser ? com.antshorttv.material.MediaCoverDeliveryService.coverUrl(entity.projectId, entity.id,
                com.antshorttv.material.MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT, entity.coverUrl) : entity.coverUrl,
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
