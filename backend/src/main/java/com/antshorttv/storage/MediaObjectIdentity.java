package com.antshorttv.storage;

public record MediaObjectIdentity(
    Long tenantId,
    Long projectId,
    String assetType,
    Long assetId,
    String versionId
) {
    public MediaObjectIdentity {
        if (tenantId == null || tenantId <= 0 || assetId == null || assetId <= 0
            || projectId != null && projectId <= 0 || blank(assetType) || blank(versionId)) {
            throw new IllegalArgumentException("媒体对象身份不完整。");
        }
        assetType = assetType.trim();
        versionId = versionId.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
