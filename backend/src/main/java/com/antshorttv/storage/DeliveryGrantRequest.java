package com.antshorttv.storage;

public record DeliveryGrantRequest(
    Long tenantId,
    Long projectId,
    Long userId,
    String resourceType,
    Long resourceId,
    String versionId,
    String renditionType,
    String objectKey,
    boolean video
) {
}
