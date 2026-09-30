package com.antshorttv.storage;

public record CreateMediaUploadSession(
    Long tenantId,
    Long projectId,
    Long userId,
    String fileName,
    String contentType,
    Long declaredSize
) {
}
