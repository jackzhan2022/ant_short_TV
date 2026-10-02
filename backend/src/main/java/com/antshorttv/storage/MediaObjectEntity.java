package com.antshorttv.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("media_object")
class MediaObjectEntity {
    @TableId(type = IdType.AUTO)
    Long id;
    Long tenantId;
    Long projectId;
    String assetType;
    Long assetId;
    String versionId;
    String renditionType;
    String objectKey;
    String mimeType;
    Long fileSize;
    String etag;
    String checksum;
    String storageClass;
    Integer width;
    Integer height;
    String status;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    MediaObjectIdentity identity() {
        return new MediaObjectIdentity(tenantId, projectId, assetType, assetId, versionId);
    }
}
