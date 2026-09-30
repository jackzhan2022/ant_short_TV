package com.antshorttv.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@TableName("media_delivery_grant")
public class MediaDeliveryGrantEntity {
    @TableId(type = IdType.AUTO)
    Long id;
    Long tenantId;
    Long projectId;
    Long userId;
    String resourceType;
    Long resourceId;
    String versionId;
    String renditionType;
    String objectKeyHash;
    LocalDateTime expiresAt;
    Integer revision;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    static MediaDeliveryGrantEntity create(
        DeliveryGrantRequest request,
        String objectKeyHash,
        Instant expiresAt,
        int revision,
        Instant now
    ) {
        MediaDeliveryGrantEntity entity = new MediaDeliveryGrantEntity();
        entity.tenantId = request.tenantId();
        entity.projectId = request.projectId();
        entity.userId = request.userId();
        entity.resourceType = request.resourceType();
        entity.resourceId = request.resourceId();
        entity.versionId = request.versionId();
        entity.renditionType = request.renditionType();
        entity.objectKeyHash = objectKeyHash;
        entity.expiresAt = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        entity.revision = revision;
        entity.createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    Instant expiryInstant() {
        return expiresAt.toInstant(ZoneOffset.UTC);
    }

    void renew(Instant expiresAt, Instant now) {
        this.expiresAt = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        this.revision = revision == null ? 1 : revision + 1;
        this.updatedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }
}
