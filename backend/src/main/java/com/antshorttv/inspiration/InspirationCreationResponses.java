package com.antshorttv.inspiration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

record InspirationCreationListResponse(
    Long id,
    String externalId,
    String externalTaskId,
    String creationType,
    String taskType,
    String title,
    String authorName,
    String url,
    String thumbnailUrl,
    String mimeType,
    Long fileSize,
    List<String> tags,
    String promptSummary,
    Integer sortOrder,
    LocalDateTime sourceCreatedAt
) {
    static InspirationCreationListResponse from(InspirationCreationEntity entity, List<String> tags) {
        return new InspirationCreationListResponse(
            entity.getId(),
            entity.getExternalId(),
            entity.getExternalTaskId(),
            entity.getCreationType(),
            entity.getTaskType(),
            entity.getTitle(),
            entity.getAuthorName(),
            entity.getUrl(),
            readyThumbnailUrl(entity),
            entity.getMimeType(),
            entity.getFileSize(),
            tags,
            promptSummary(entity.getPromptText()),
            entity.getSortOrder(),
            entity.getSourceCreatedAt()
        );
    }

    private static String promptSummary(String promptText) {
        if (promptText == null || promptText.isBlank()) {
            return null;
        }
        String normalized = promptText.trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private static String readyThumbnailUrl(InspirationCreationEntity entity) {
        return "READY".equals(entity.getThumbnailStatus()) ? entity.getThumbnailUrl() : null;
    }
}

record InspirationCreationDetailResponse(
    Long id,
    String externalId,
    String externalTaskId,
    String creationType,
    String taskType,
    String title,
    String authorName,
    String url,
    String thumbnailUrl,
    String mimeType,
    Long fileSize,
    List<String> tags,
    String promptText,
    Integer sortOrder,
    LocalDateTime sourceCreatedAt,
    JsonNode detailJson
) {
    static InspirationCreationDetailResponse from(
        InspirationCreationEntity entity,
        List<String> tags,
        JsonNode detailJson
    ) {
        return new InspirationCreationDetailResponse(
            entity.getId(),
            entity.getExternalId(),
            entity.getExternalTaskId(),
            entity.getCreationType(),
            entity.getTaskType(),
            entity.getTitle(),
            entity.getAuthorName(),
            entity.getUrl(),
            "READY".equals(entity.getThumbnailStatus()) ? entity.getThumbnailUrl() : null,
            entity.getMimeType(),
            entity.getFileSize(),
            tags,
            entity.getPromptText(),
            entity.getSortOrder(),
            entity.getSourceCreatedAt(),
            detailJson
        );
    }
}

record InspirationCreationPageResponse(
    List<InspirationCreationListResponse> records,
    long total,
    int current,
    int pageSize
) {
}
