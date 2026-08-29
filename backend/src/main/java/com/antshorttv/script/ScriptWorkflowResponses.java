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
    ScriptAnalysisTaskResponse analysis
) {
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
            stages.stream().map(stage -> ScriptAnalysisStageResponse.from(stage, resultsByStageId.get(stage.getId()))).toList()
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
    String resultJson,
    String providerRequestId,
    Long aiCallLogId,
    Long durationMs,
    String resultErrorCode,
    String resultErrorMessage,
    Boolean resultRetryable
) {
    static ScriptAnalysisStageResponse from(ScriptAnalysisStageEntity stage, ScriptAnalysisResultEntity result) {
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
            result == null ? null : result.getNormalizedJson(),
            result == null ? null : result.getProviderRequestId(),
            result == null ? null : result.getAiCallLogId(),
            result == null ? null : result.getDurationMs(),
            result == null ? null : result.getErrorCode(),
            result == null ? null : result.getErrorMessage(),
            result == null ? null : result.getRetryable()
        );
    }
}
