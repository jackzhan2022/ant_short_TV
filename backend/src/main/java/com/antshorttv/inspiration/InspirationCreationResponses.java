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
    String mimeType,
    Long fileSize,
    Integer sortOrder,
    LocalDateTime sourceCreatedAt
) {
    static InspirationCreationListResponse from(InspirationCreationEntity entity) {
        return new InspirationCreationListResponse(
            entity.getId(),
            entity.getExternalId(),
            entity.getExternalTaskId(),
            entity.getCreationType(),
            entity.getTaskType(),
            entity.getTitle(),
            entity.getAuthorName(),
            entity.getUrl(),
            entity.getMimeType(),
            entity.getFileSize(),
            entity.getSortOrder(),
            entity.getSourceCreatedAt()
        );
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
    String mimeType,
    Long fileSize,
    Integer sortOrder,
    LocalDateTime sourceCreatedAt,
    JsonNode detailJson
) {
    static InspirationCreationDetailResponse from(InspirationCreationEntity entity, JsonNode detailJson) {
        return new InspirationCreationDetailResponse(
            entity.getId(),
            entity.getExternalId(),
            entity.getExternalTaskId(),
            entity.getCreationType(),
            entity.getTaskType(),
            entity.getTitle(),
            entity.getAuthorName(),
            entity.getUrl(),
            entity.getMimeType(),
            entity.getFileSize(),
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
