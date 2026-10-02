package com.antshorttv.storage;

public record RegisteredMediaObject(
    Long id,
    MediaObjectIdentity identity,
    String renditionType,
    String objectKey,
    String status
) {
}
