package com.antshorttv.storage;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaUploadSessionService {
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/", "video/", "audio/", "text/vtt", "application/x-subrip"
    );

    private final MediaUploadSessionStore store;
    private final CosUploadRequestAuthorizer authorizer;
    private final ObjectStorageKeyFactory keys;
    private final ObjectStorageService storage;
    private final ObjectStorageProperties properties;
    private final Clock clock;

    @Autowired
    public MediaUploadSessionService(
        MediaUploadSessionStore store,
        CosUploadRequestAuthorizer authorizer,
        ObjectStorageKeyFactory keys,
        ObjectStorageService storage,
        ObjectStorageProperties properties
    ) {
        this(store, authorizer, keys, storage, properties, Clock.systemUTC());
    }

    MediaUploadSessionService(
        MediaUploadSessionStore store,
        CosUploadRequestAuthorizer authorizer,
        ObjectStorageKeyFactory keys,
        ObjectStorageService storage,
        ObjectStorageProperties properties,
        Clock clock
    ) {
        this.store = store;
        this.authorizer = authorizer;
        this.keys = keys;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MediaUploadSession create(CreateMediaUploadSession command) {
        validate(command);
        Instant now = clock.instant();
        String token = UUID.randomUUID().toString();
        String fileName = "source." + extension(command.fileName());
        String objectKey = command.projectId() == null
            ? keys.tenantUpload(command.tenantId(), token, fileName)
            : keys.projectUpload(command.tenantId(), command.projectId(), token, fileName);
        MediaUploadSessionEntity entity = new MediaUploadSessionEntity();
        entity.tenantId = command.tenantId();
        entity.projectId = command.projectId();
        entity.userId = command.userId();
        entity.sessionToken = token;
        entity.objectKey = objectKey;
        entity.fileName = command.fileName();
        entity.contentType = command.contentType();
        entity.declaredSize = command.declaredSize();
        entity.status = "PENDING";
        entity.expiresAt = LocalDateTime.ofInstant(now.plusSeconds(604800), ZoneOffset.UTC);
        entity.createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        entity.updatedAt = entity.createdAt;
        store.insert(entity);
        return response(entity);
    }

    @Transactional
    public CosUploadAuthorization authorize(
        Long userId,
        String sessionToken,
        CosUploadAuthorizationRequest request
    ) {
        MediaUploadSessionEntity entity = requireOwned(userId, sessionToken);
        if (!"PENDING".equals(entity.status)) {
            throw new IllegalArgumentException("当前上传会话不可签名。");
        }
        return authorizer.authorize(entity.objectKey, request);
    }

    @Transactional
    public VerifiedMediaUpload complete(Long userId, String sessionToken) {
        MediaUploadSessionEntity entity = requireOwned(userId, sessionToken);
        if ("COMPLETED".equals(entity.status)) {
            return verified(entity);
        }
        if (!"PENDING".equals(entity.status)) {
            throw new IllegalArgumentException("当前上传会话不可完成。");
        }
        String stagingKey = entity.objectKey;
        StoredObject staged = storage.metadata(stagingKey);
        if (entity.declaredSize != null && entity.declaredSize != staged.size()) {
            throw new IllegalArgumentException("上传文件大小与会话声明不一致。");
        }
        if (!entity.contentType.equalsIgnoreCase(staged.contentType())) {
            throw new IllegalArgumentException("上传文件类型与会话声明不一致。");
        }
        if (staged.storageClass() == null
            || !properties.getStorageClass().equalsIgnoreCase(staged.storageClass())) {
            throw new IllegalArgumentException("上传对象存储类型与配置不一致。");
        }
        LocalDate createdDate = entity.createdAt.toLocalDate();
        String durableKey = keys.verifiedUploadOriginal(
            entity.tenantId,
            entity.projectId,
            entity.sessionToken,
            createdDate,
            extension(entity.fileName)
        );
        StoredObject object = storage.promoteVerifiedUpload(staged, durableKey);
        Instant now = clock.instant();
        entity.objectKey = object.key();
        entity.verifiedSize = object.size();
        entity.etag = object.eTag();
        entity.status = "COMPLETED";
        entity.completedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        entity.updatedAt = entity.completedAt;
        store.update(entity);
        return verified(entity);
    }

    @Transactional
    public VerifiedMediaUpload requireCompleted(Long userId, String sessionToken) {
        MediaUploadSessionEntity entity = requireOwned(userId, sessionToken);
        if (!"COMPLETED".equals(entity.status)) {
            throw new IllegalArgumentException("上传会话尚未完成校验。");
        }
        return verified(entity);
    }

    @Transactional
    public VerifiedMediaUpload requireCompleted(Long userId, Long tenantId, String sessionToken) {
        MediaUploadSessionEntity entity = requireOwned(userId, sessionToken);
        if (!entity.tenantId.equals(tenantId)) {
            throw new IllegalArgumentException("上传会话不存在。");
        }
        if (!"COMPLETED".equals(entity.status)) {
            throw new IllegalArgumentException("上传会话尚未完成校验。");
        }
        return verified(entity);
    }

    @Transactional
    public MediaUploadSession status(Long userId, String sessionToken) {
        return response(requireOwned(userId, sessionToken));
    }

    @Transactional
    public void cancel(Long userId, String sessionToken) {
        MediaUploadSessionEntity entity = requireOwned(userId, sessionToken);
        if ("COMPLETED".equals(entity.status)) {
            throw new IllegalArgumentException("已完成的上传不能取消。");
        }
        entity.status = "CANCELED";
        entity.updatedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        store.update(entity);
    }

    private MediaUploadSessionEntity requireOwned(Long userId, String token) {
        MediaUploadSessionEntity entity = store.find(token);
        if (entity == null || !entity.userId.equals(userId)) {
            throw new IllegalArgumentException("上传会话不存在。");
        }
        if (entity.expiresAt.isBefore(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw new IllegalArgumentException("上传会话已过期。");
        }
        return entity;
    }

    private MediaUploadSession response(MediaUploadSessionEntity entity) {
        return new MediaUploadSession(
            entity.sessionToken,
            properties.getBucket(),
            properties.getRegion(),
            properties.getStorageClass(),
            entity.objectKey,
            entity.status,
            entity.expiresAt.toInstant(ZoneOffset.UTC)
        );
    }

    private VerifiedMediaUpload verified(MediaUploadSessionEntity entity) {
        return new VerifiedMediaUpload(
            entity.sessionToken,
            entity.objectKey,
            entity.contentType,
            entity.verifiedSize,
            entity.etag
        );
    }

    private void validate(CreateMediaUploadSession command) {
        if (command == null || command.tenantId() == null || command.userId() == null
            || command.fileName() == null || command.fileName().isBlank()
            || command.contentType() == null || command.contentType().isBlank()) {
            throw new IllegalArgumentException("上传会话参数不完整。");
        }
        String contentType = command.contentType().toLowerCase(Locale.ROOT);
        if (ALLOWED_TYPES.stream().noneMatch(contentType::startsWith)) {
            throw new IllegalArgumentException("不支持的上传文件类型。");
        }
        if (command.declaredSize() != null && command.declaredSize() < 0) {
            throw new IllegalArgumentException("上传文件大小不合法。");
        }
    }

    private String extension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator < 0 || separator == fileName.length() - 1) {
            return "bin";
        }
        String extension = fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]{1,10}")) {
            throw new IllegalArgumentException("上传文件扩展名不合法。");
        }
        return extension;
    }
}
