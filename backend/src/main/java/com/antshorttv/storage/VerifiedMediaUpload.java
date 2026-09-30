package com.antshorttv.storage;

public record VerifiedMediaUpload(
    String sessionToken,
    String objectKey,
    String contentType,
    long size,
    String eTag
) {
}
