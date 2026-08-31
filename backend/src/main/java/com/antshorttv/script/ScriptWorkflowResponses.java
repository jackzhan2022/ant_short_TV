package com.antshorttv.script;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

record ScriptResponse(
    Long id,
    Long projectId,
    String title,
    String sourceType,
    String content,
    String status,
    Long currentVersionId,
    LocalDateTime updatedAt
) {
    static ScriptResponse from(ScriptEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ScriptResponse(
            entity.getId(),
            entity.getProjectId(),
            entity.getTitle(),
            entity.getSourceType(),
            entity.getContent(),
            entity.getStatus(),
            entity.getCurrentVersionId(),
            entity.getUpdatedAt()
        );
    }
}

record ScriptVersionResponse(
    Long id,
    Long scriptId,
    Integer versionNo,
    String sourceType,
    String inputSummary,
    String content,
    String status,
    LocalDateTime createdAt
) {
    static ScriptVersionResponse from(ScriptVersionEntity entity) {
        return new ScriptVersionResponse(
            entity.getId(),
            entity.getScriptId(),
            entity.getVersionNo(),
            entity.getSourceType(),
            entity.getInputSummary(),
            entity.getContent(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}

record CharacterAssetResponse(
    Long id,
    String name,
    String roleType,
    String gender,
    String ageRange,
    String identity,
    List<String> personality,
    String appearance,
    String prompt,
    String status,
    Long mergeTargetId,
    AssetVisualWorkspace visual
) {
}

record SceneAssetResponse(
    Long id,
    String name,
    String sceneType,
    String atmosphere,
    String description,
    String visualStyle,
    String prompt,
    String status,
    Long mergeTargetId,
    AssetVisualWorkspace visual
) {
}

record PropAssetResponse(
    Long id,
    String name,
    String propType,
    String appearance,
    String plotFunction,
    String prompt,
    String status,
    Long mergeTargetId,
    AssetVisualWorkspace visual
) {
}

record AssetVisualWorkspace(
    int variantCount,
    AssetVisualVariantService.VariantResponse primaryVariant,
    List<AssetVisualVariantService.VariantResponse> variants,
    Map<String, Long> generationSummary,
    List<AssetVisualBindingService.BindingResponse> episodeBindings,
    String normalizationReviewStatus,
    String resolvedImageUrl,
    String resolvedImageSource
) {
}

record StoryboardResponse(
    Long id,
    Integer shotNo,
    Integer episodeNo,
    String shotType,
    String visualDescription,
    String characters,
    String scene,
    String dialogue,
    Integer durationSeconds,
    String imagePrompt,
    String videoPrompt,
    String firstFrameUrl,
    Long currentVideoResultId,
    String currentVideoUrl
) {
    static StoryboardResponse from(StoryboardEntity entity) {
        return new StoryboardResponse(
            entity.id,
            entity.shotNo,
            entity.episodeNo,
            null,
            entity.visualDescription,
            entity.characters,
            entity.scene,
            null,
            entity.durationSeconds,
            entity.imagePrompt,
            entity.videoPrompt,
            entity.firstFrameUrl,
            entity.currentVideoResultId,
            entity.currentVideoUrl
        );
    }
}

record ScriptWorkspaceResponse(
    Long projectId,
    ScriptResponse script,
    List<ScriptVersionResponse> versions,
    List<CharacterAssetResponse> characters,
    List<SceneAssetResponse> scenes,
    List<PropAssetResponse> props,
    List<StoryboardResponse> storyboards,
    List<ScriptEpisodeResponse> episodes,
    ScriptAnalysisTaskResponse analysis,
    ScriptGlobalUnderstandingResponse globalUnderstanding
) {
}

record ScriptGlobalUnderstandingResponse(
    Long id,
    Integer schemaVersion,
    com.fasterxml.jackson.databind.JsonNode content,
    String analyzedContentHash,
    Long lastAgentRunId,
    java.time.LocalDateTime updatedAt
) {
    static ScriptGlobalUnderstandingResponse from(ScriptGlobalUnderstandingDocument document) {
        if (document == null) {
            return null;
        }
        return new ScriptGlobalUnderstandingResponse(
            document.id(), document.schemaVersion(), document.content(),
            document.analyzedContentHash(), document.lastAgentRunId(), document.updatedAt());
    }
}

record ScriptAnalysisTaskResponse(
    Long id,
    Long scriptVersionId,
    String status,
    String currentStage,
    Integer overallProgress,
    String currentAction,
    String errorCode,
    String errorMessage,
    List<ScriptAnalysisStageResponse> stages
) {
    static ScriptAnalysisTaskResponse from(
        ScriptAnalysisTaskEntity task,
        List<ScriptAnalysisStageEntity> stages,
        Map<Long, ScriptAnalysisResultEntity> resultsByStageId
    ) {
        return from(task, stages, resultsByStageId, Map.of(), Map.of());
    }

    static ScriptAnalysisTaskResponse from(
        ScriptAnalysisTaskEntity task,
        List<ScriptAnalysisStageEntity> stages,
        Map<Long, ScriptAnalysisResultEntity> resultsByStageId,
        Map<Long, Long> agentRunsByStageId
    ) {
        return from(task, stages, resultsByStageId, agentRunsByStageId, Map.of());
    }

    static ScriptAnalysisTaskResponse from(
        ScriptAnalysisTaskEntity task,
        List<ScriptAnalysisStageEntity> stages,
        Map<Long, ScriptAnalysisResultEntity> resultsByStageId,
        Map<Long, Long> agentRunsByStageId,
        Map<Long, EpisodeFanoutProgressResponse> fanoutByStageId
    ) {
        return from(task, stages, resultsByStageId, agentRunsByStageId, fanoutByStageId, Map.of());
    }

    static ScriptAnalysisTaskResponse from(
        ScriptAnalysisTaskEntity task,
        List<ScriptAnalysisStageEntity> stages,
        Map<Long, ScriptAnalysisResultEntity> resultsByStageId,
        Map<Long, Long> agentRunsByStageId,
        Map<Long, EpisodeFanoutProgressResponse> fanoutByStageId,
        Map<Long, EpisodeSplitProgressResponse> splitProgressByStageId
    ) {
        if (task == null) {
            return null;
        }
        return new ScriptAnalysisTaskResponse(
            task.getId(),
            task.getScriptVersionId(),
            task.getStatus(),
            task.getCurrentStage(),
            task.getOverallProgress(),
            task.getCurrentAction(),
            task.getErrorCode(),
            task.getErrorMessage(),
            stages.stream().map(stage -> ScriptAnalysisStageResponse.from(
                stage, resultsByStageId.get(stage.getId()), agentRunsByStageId.get(stage.getId()),
                fanoutByStageId.get(stage.getId()), splitProgressByStageId.get(stage.getId()))).toList()
        );
    }
}

record ScriptAnalysisStageResponse(
    Long id,
    String stageCode,
    Integer stageOrder,
    String status,
    Integer progressPercent,
    Integer completedUnits,
    Integer totalUnits,
    String currentAction,
    String errorCode,
    String errorMessage,
    Boolean retryable,
    Long agentRunId,
    String resultJson,
    String providerRequestId,
    Long aiCallLogId,
    Long durationMs,
    String resultErrorCode,
    String resultErrorMessage,
    Boolean resultRetryable,
    EpisodeFanoutProgressResponse fanout,
    EpisodeSplitProgressResponse splitProgress
) {
    static ScriptAnalysisStageResponse from(ScriptAnalysisStageEntity stage, ScriptAnalysisResultEntity result) {
        return from(stage, result, null, null, null);
    }

    static ScriptAnalysisStageResponse from(
        ScriptAnalysisStageEntity stage,
        ScriptAnalysisResultEntity result,
        Long agentRunId
    ) {
        return from(stage, result, agentRunId, null, null);
    }

    static ScriptAnalysisStageResponse from(
        ScriptAnalysisStageEntity stage,
        ScriptAnalysisResultEntity result,
        Long agentRunId,
        EpisodeFanoutProgressResponse fanout
    ) {
        return from(stage, result, agentRunId, fanout, null);
    }

    static ScriptAnalysisStageResponse from(
        ScriptAnalysisStageEntity stage,
        ScriptAnalysisResultEntity result,
        Long agentRunId,
        EpisodeFanoutProgressResponse fanout,
        EpisodeSplitProgressResponse splitProgress
    ) {
        return new ScriptAnalysisStageResponse(
            stage.getId(),
            stage.getStageCode(),
            stage.getStageOrder(),
            stage.getStatus(),
            stage.getProgressPercent(),
            stage.getCompletedUnits(),
            stage.getTotalUnits(),
            stage.getCurrentAction(),
            stage.getErrorCode(),
            stage.getErrorMessage(),
            stage.getRetryable(),
            agentRunId,
            result == null ? null : result.getNormalizedJson(),
            result == null ? null : result.getProviderRequestId(),
            result == null ? null : result.getAiCallLogId(),
            result == null ? null : result.getDurationMs(),
            result == null ? null : result.getErrorCode(),
            result == null ? null : result.getErrorMessage(),
            result == null ? null : result.getRetryable(),
            fanout,
            splitProgress
        );
    }
}

record EpisodeFanoutProgressResponse(
    Long snapshotId,
    String status,
    Integer total,
    Integer completed,
    Integer failed,
    Long currentEpisodeId,
    String currentEpisodeKey,
    Boolean retryable,
    Boolean stale,
    List<EpisodeFanoutUnitResponse> units
) {}

record EpisodeSplitProgressResponse(
    String mode,
    String fallbackReason,
    Integer totalChunks,
    Integer completedChunks,
    Integer failedChunks,
    Boolean stale
) {}

record EpisodeFanoutUnitResponse(
    Long episodeId,
    String episodeKey,
    String status,
    Long childRunId,
    String errorCode,
    String errorMessage
) {}
