package com.antshorttv.project;

import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.RegisteredMediaDetails;

public class ProjectCoverObject {
    public Long id;
    public Long tenantId;
    public Long projectId;
    public String assetType;
    public Long assetId;
    public String versionId;
    public String renditionType;
    public String objectKey;
    public String mimeType;
    public long fileSize;
    public String status;
    public String errorMessage;

    MediaObjectIdentity identity() {
        return new MediaObjectIdentity(tenantId, projectId, assetType, assetId, versionId);
    }

    RegisteredMediaDetails details() {
        return new RegisteredMediaDetails(id, identity(), renditionType, objectKey, mimeType,
            fileSize, status, errorMessage);
    }
}
