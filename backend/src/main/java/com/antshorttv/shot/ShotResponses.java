package com.antshorttv.shot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.io.Resource;

record AiVoiceTaskResponse(
    Long id,
    Long projectId,
    Long storyboardId,
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

record EpisodeComposeItemResponse(
    Long id,
    Long taskId,
    Integer episodeNo,
    Long storyboardId,
    Integer storyboardOrder,
    Long shotResultId,
    String videoUrl,
    BigDecimal durationSeconds,
    Integer width,
    Integer height,
    String status,
    String errorMessage,
    LocalDateTime createdAt
) {
    static EpisodeComposeItemResponse from(EpisodeComposeItemEntity entity) {
        return new EpisodeComposeItemResponse(
            entity.id,
            entity.taskId,
            entity.episodeNo,
            entity.storyboardId,
            entity.storyboardOrder,
            entity.shotResultId,
            entity.videoUrl,
            entity.durationSeconds,
            entity.width,
            entity.height,
            entity.status,
            entity.errorMessage,
            entity.createdAt
        );
    }
}

record EpisodeVideoVersionResponse(
    Long id,
    Integer episodeNo,
    Long composeTaskId,
    Integer versionNo,
    String versionName,
    String videoUrl,
    String storagePath,
    String coverUrl,
    BigDecimal durationSeconds,
    Integer width,
    Integer height,
    Long fileSize,
    String format,
    Long materialId,
    Boolean current,
    String status,
    LocalDateTime createdAt
) {
    static EpisodeVideoVersionResponse from(EpisodeVideoVersionEntity entity) {
        return new EpisodeVideoVersionResponse(
            entity.id,
            entity.episodeNo,
            entity.composeTaskId,
            entity.versionNo,
            entity.versionName,
            entity.videoUrl,
            entity.storagePath,
            entity.coverUrl,
            entity.durationSeconds,
            entity.width,
            entity.height,
            entity.fileSize,
            entity.format,
            entity.materialId,
            entity.isCurrent,
            entity.status,
            entity.createdAt
        );
    }
}

record EpisodeComposeTaskResponse(
    Long id,
    Long projectId,
    Integer episodeNo,
    String taskName,
    String composeConfig,
    Integer storyboardCount,
    BigDecimal totalDurationSeconds,
    String status,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    List<EpisodeComposeItemResponse> items,
    EpisodeVideoVersionResponse videoVersion
) {
    static EpisodeComposeTaskResponse from(
        EpisodeComposeTaskEntity entity,
        List<EpisodeComposeItemEntity> items,
        EpisodeVideoVersionEntity version
    ) {
        return new EpisodeComposeTaskResponse(
            entity.id,
            entity.projectId,
            entity.episodeNo,
            entity.taskName,
            entity.composeConfig,
            entity.storyboardCount,
            entity.totalDurationSeconds,
            entity.status,
            entity.errorMessage,
            entity.startedAt,
            entity.completedAt,
            entity.createdAt,
            items.stream().map(EpisodeComposeItemResponse::from).toList(),
            version == null ? null : EpisodeVideoVersionResponse.from(version)
        );
    }
}

record EpisodeExportRecordResponse(
    Long id,
    Integer episodeNo,
    Long videoVersionId,
    String exportType,
    String exportStatus,
    String fileName,
    Long fileSize,
    String downloadUrl,
    String errorMessage,
    LocalDateTime createdAt
) {
    static EpisodeExportRecordResponse from(EpisodeExportRecordEntity entity) {
        return new EpisodeExportRecordResponse(
            entity.id,
            entity.episodeNo,
            entity.videoVersionId,
            entity.exportType,
            entity.exportStatus,
            entity.fileName,
            entity.fileSize,
            entity.downloadUrl,
            entity.errorMessage,
            entity.createdAt
        );
    }
}

record EpisodeVideoDownloadResource(
    Resource resource,
    String fileName,
    Long fileSize
) {
}
