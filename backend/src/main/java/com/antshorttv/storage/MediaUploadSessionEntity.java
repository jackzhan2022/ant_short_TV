package com.antshorttv.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("media_upload_session")
public class MediaUploadSessionEntity {
    @TableId(type = IdType.AUTO)
    Long id;
    Long tenantId;
    Long projectId;
    Long userId;
    String sessionToken;
    String objectKey;
    String fileName;
    String contentType;
    Long declaredSize;
    Long verifiedSize;
    String etag;
    String status;
    LocalDateTime expiresAt;
    LocalDateTime completedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
