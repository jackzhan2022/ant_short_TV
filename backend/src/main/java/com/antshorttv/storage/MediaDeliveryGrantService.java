package com.antshorttv.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaDeliveryGrantService {
    private final MediaDeliveryGrantStore store;
    private final ObjectStorageProperties properties;
    private final Clock clock;
    private final CdnTypeDSigner signer;

    @Autowired
    public MediaDeliveryGrantService(MediaDeliveryGrantStore store, ObjectStorageProperties properties) {
        this(store, properties, Clock.systemUTC());
    }

    MediaDeliveryGrantService(MediaDeliveryGrantStore store, ObjectStorageProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.signer = new CdnTypeDSigner(properties.getCdnDomain(), properties.getCdnTypeDKey());
    }

    @Transactional
    public DeliveryGrant issue(DeliveryGrantRequest request) {
        validate(request);
        Instant now = clock.instant();
        String hash = objectKeyHash(request.objectKey());
        MediaDeliveryGrantEntity entity = store.find(request.userId(), hash);
        if (entity == null) {
            entity = MediaDeliveryGrantEntity.create(
                request,
                hash,
                now.plusSeconds(properties.getCdnAuthorizationSeconds()),
                1,
                now
            );
            try {
                store.insert(entity);
            } catch (DuplicateKeyException exception) {
                entity = store.find(request.userId(), hash);
                if (entity == null) {
                    throw exception;
                }
            }
        }

        Instant expiresAt = entity.expiryInstant();
        long threshold = request.video() ? properties.getVideoRenewalThresholdSeconds() : 0;
        if (!expiresAt.isAfter(now.plusSeconds(threshold))) {
            expiresAt = now.plusSeconds(properties.getCdnAuthorizationSeconds());
            entity.renew(expiresAt, now);
            store.update(entity);
        }
        return new DeliveryGrant(signer.sign(request.objectKey(), expiresAt), expiresAt);
    }

    static String objectKeyHash(String objectKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(objectKey.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算对象键摘要。", exception);
        }
    }

    private void validate(DeliveryGrantRequest request) {
        if (request == null || request.tenantId() == null || request.userId() == null
            || request.resourceId() == null || request.resourceType() == null || request.resourceType().isBlank()
            || request.versionId() == null || request.versionId().isBlank()
            || request.renditionType() == null || request.renditionType().isBlank()
            || request.objectKey() == null || request.objectKey().isBlank()) {
            throw new IllegalArgumentException("媒体访问授权参数不完整。");
        }
    }
}
