package com.antshorttv.inspiration;

import java.time.LocalDateTime;
import java.util.List;

record InspirationCreateMetadata(
    String title,
    List<String> tags,
    String promptText,
    String publishStatus
) {
}

record InspirationMetadataRequest(
    String title,
    List<String> tags,
    String promptText
) {
}

record InspirationPublishStatusRequest(String publishStatus) {
}

record InspirationReorderRequest(List<Long> orderedIds) {
}

record InspirationManagementItemResponse(
    Long id,
    String title,
    List<String> tags,
    String promptText,
    String creationType,
    String mimeType,
    String url,
    String thumbnailUrl,
    String publishStatus,
    String sourceType,
    Integer sortOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

record InspirationManagementPageResponse(
    List<InspirationManagementItemResponse> records,
    long total,
    int current,
    int pageSize
) {
}
