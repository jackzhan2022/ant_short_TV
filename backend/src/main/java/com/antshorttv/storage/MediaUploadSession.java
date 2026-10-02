package com.antshorttv.storage;

import java.time.Instant;

public record MediaUploadSession(
    String sessionToken,
    String bucket,
    String region,
    String storageClass,
    String objectKey,
    String status,
    Instant expiresAt
) {
}
