package com.antshorttv.storage;

public record RegisteredImageOriginal(
    String objectKey,
    String mimeType,
    String storageClass,
    String status
) {
}
