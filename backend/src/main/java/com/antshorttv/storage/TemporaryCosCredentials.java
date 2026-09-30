package com.antshorttv.storage;

public record TemporaryCosCredentials(
    String tmpSecretId,
    String tmpSecretKey,
    String sessionToken,
    long expiredTime,
    String requestId
) {
}
