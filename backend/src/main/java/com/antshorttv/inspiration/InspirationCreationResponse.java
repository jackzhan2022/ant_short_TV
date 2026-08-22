package com.antshorttv.inspiration;

import java.time.LocalDateTime;

record InspirationCreationResponse(
    Long id,
    String externalId,
    String creationType,
    String taskType,
    String title,
    String authorName,
    String localUrl,
    String mimeType,
    Integer sortOrder,
    LocalDateTime sourceCreatedAt
) {
}
