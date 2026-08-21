package com.antshorttv.script;

import java.time.LocalDateTime;
import java.util.List;

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
    Long mergeTargetId
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
    Long mergeTargetId
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
    Long mergeTargetId
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
    List<StoryboardResponse> storyboards
) {
}
