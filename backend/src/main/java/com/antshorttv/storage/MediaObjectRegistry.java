package com.antshorttv.storage;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaObjectRegistry {
    private final MediaObjectStore store;
    private final ObjectStorageKeyFactory keys;

    public MediaObjectRegistry(MediaObjectStore store, ObjectStorageKeyFactory keys) {
        this.store = store;
        this.keys = keys;
    }

    @Transactional
    public RegisteredMediaObject registerOriginal(
        MediaObjectIdentity identity,
        StoredObject object,
        String checksum,
        int width,
        int height
    ) {
        if (identity == null || object == null || object.size() < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("原始媒体元数据不完整。");
        }
        String objectKey = keys.objectKey(object.key());
        requireText(object.contentType(), "MIME 类型");
        requireText(object.storageClass(), "存储类型");
        return register(
            identity, "ORIGINAL", objectKey, object.contentType(), object.size(), object.eTag(),
            checksum, object.storageClass(), width, height, "READY"
        );
    }

    @Transactional
    public RegisteredMediaObject registerPendingRendition(
        MediaObjectIdentity identity,
        String renditionType,
        String objectKey,
        String mimeType,
        String storageClass
    ) {
        if (identity == null) throw new IllegalArgumentException("媒体对象身份不能为空。");
        String normalizedKey = keys.objectKey(objectKey);
        if (!normalizedKey.contains("/derived/")) {
            throw new IllegalArgumentException("派生媒体对象必须位于 derived 命名空间。");
        }
        String normalizedType = requireText(renditionType, "派生类型").toUpperCase();
        return register(
            identity, normalizedType, normalizedKey, requireText(mimeType, "MIME 类型"), 0L,
            null, null, requireText(storageClass, "存储类型"), null, null, "PENDING"
        );
    }

    @Transactional
    public RegisteredMediaObject find(MediaObjectIdentity identity, String renditionType) {
        if (identity == null) throw new IllegalArgumentException("媒体对象身份不能为空。");
        MediaObjectEntity entity = store.find(
            identity, requireText(renditionType, "派生类型").toUpperCase()
        );
        return entity == null ? null : response(entity);
    }

    private RegisteredMediaObject register(
        MediaObjectIdentity identity,
        String renditionType,
        String objectKey,
        String mimeType,
        long fileSize,
        String eTag,
        String checksum,
        String storageClass,
        Integer width,
        Integer height,
        String status
    ) {
        MediaObjectEntity existing = store.find(identity, renditionType);
        if (existing != null) {
            if (!existing.identity().equals(identity) || !existing.objectKey.equals(objectKey)) {
                throw new IllegalStateException("不可变媒体对象不能替换为其他对象键或归属。");
            }
            return response(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        MediaObjectEntity entity = new MediaObjectEntity();
        entity.tenantId = identity.tenantId();
        entity.projectId = identity.projectId();
        entity.assetType = identity.assetType();
        entity.assetId = identity.assetId();
        entity.versionId = identity.versionId();
        entity.renditionType = renditionType;
        entity.objectKey = objectKey;
        entity.mimeType = mimeType;
        entity.fileSize = fileSize;
        entity.etag = eTag;
        entity.checksum = checksum;
        entity.storageClass = storageClass;
        entity.width = width;
        entity.height = height;
        entity.status = status;
        entity.createdAt = now;
        entity.updatedAt = now;
        store.insert(entity);
        return response(entity);
    }

    private RegisteredMediaObject response(MediaObjectEntity entity) {
        return new RegisteredMediaObject(
            entity.id, entity.identity(), entity.renditionType, entity.objectKey, entity.status
        );
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空。");
        }
        return value.trim();
    }
}
