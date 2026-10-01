package com.antshorttv.storage;

public record RegisteredMediaDetails(
    Long id,
    MediaObjectIdentity identity,
    String renditionType,
    String objectKey,
    String mimeType,
    long fileSize,
    String status,
    String errorMessage
) {
}
