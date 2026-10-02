package com.antshorttv.storage;

public record CosUploadAuthorization(
    String authorization,
    String securityToken,
    long expiresAt
) {
}
