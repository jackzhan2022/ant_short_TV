package com.antshorttv.shot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

record AiVoiceTaskResponse(
    Long id,
    Long projectId,
    Long storyboardId,
    Long serviceConfigId,
    String providerCode,
    String model,
    String voiceType,
    String speakerName,
    String voiceId,
    String textContent,
    BigDecimal speed,
    BigDecimal pitch,
    BigDecimal volume,
    String status,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    List<AiVoiceResultResponse> results
) {
    static AiVoiceTaskResponse from(AiVoiceTaskEntity entity, List<AiVoiceResultEntity> results) {
        return new AiVoiceTaskResponse(
            entity.id,
            entity.projectId,
            entity.storyboardId,
            entity.serviceConfigId,
            entity.providerCode,
            entity.model,
            entity.voiceType,
            entity.speakerName,
            entity.voiceId,
            entity.textContent,
            entity.speed,
            entity.pitch,
            entity.volume,
            entity.status,
            entity.errorMessage,
            entity.startedAt,
            entity.completedAt,
            entity.createdAt,
            results.stream().map(AiVoiceResultResponse::from).toList()
        );
    }
}

record AiVoiceResultResponse(
    Long id,
    Long taskId,
    Long storyboardId,
    String audioUrl,
    String storagePath,
    BigDecimal durationSeconds,
    Long fileSize,
    String format,
    Long materialId,
    Boolean selected,
    String status,
    LocalDateTime createdAt
) {
    static AiVoiceResultResponse from(AiVoiceResultEntity entity) {
        return new AiVoiceResultResponse(
            entity.id,
            entity.taskId,
            entity.storyboardId,
            entity.audioUrl,
            entity.storagePath,
            entity.durationSeconds,
            entity.fileSize,
            entity.format,
            entity.materialId,
            entity.isSelected,
            entity.status,
            entity.createdAt
        );
    }
}

record SubtitleSegmentResponse(String text, BigDecimal startTime, BigDecimal endTime) {
}

record StoryboardSubtitleResponse(
    Long id,
    Long storyboardId,
    Long voiceResultId,
    String subtitleType,
    String textContent,
    String srtUrl,
    String styleConfig,
    Boolean selected,
    String status,
    LocalDateTime createdAt,
    List<SubtitleSegmentResponse> segments
) {
}

record ShotComposeTaskResponse(
    Long id,
    Long projectId,
    Long storyboardId,
    Long voiceResultId,
    Long subtitleId,
    String composeConfig,
    String status,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    List<ShotComposeResultResponse> results
) {
    static ShotComposeTaskResponse from(ShotComposeTaskEntity entity, List<ShotComposeResultEntity> results) {
        return new ShotComposeTaskResponse(
            entity.id,
            entity.projectId,
            entity.storyboardId,
            entity.voiceResultId,
            entity.subtitleId,
            entity.composeConfig,
            entity.status,
            entity.errorMessage,
            entity.startedAt,
            entity.completedAt,
            entity.createdAt,
            results.stream().map(ShotComposeResultResponse::from).toList()
        );
    }
}

record ShotComposeResultResponse(
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
    Boolean selected,
    String status,
    LocalDateTime createdAt
) {
    static ShotComposeResultResponse from(ShotComposeResultEntity entity) {
        return new ShotComposeResultResponse(
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
