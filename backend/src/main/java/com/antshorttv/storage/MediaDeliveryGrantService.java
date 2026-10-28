package com.antshorttv.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Objects;
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
        this.signer = new CdnTypeDSigner(properties.getCdnDomain(), properties.getCdnTypeDKey(), properties.getCdnTypeDExpirySeconds());
    }

    @Transactional
    public DeliveryGrant issue(DeliveryGrantRequest request) {
        validate(request);
        // Match DATETIME's second precision so the first URL survives database round-tripping.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
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

    /** One user/tenant per batch. No signed URLs or permissions are cached between requests. */
    @Transactional
    public Map<String, DeliveryGrant> issueAll(List<DeliveryGrantRequest> requests) {
        if (requests.isEmpty()) return Map.of();
        var first = requests.get(0);
        validate(first);
        Map<String, DeliveryGrantRequest> unique = new TreeMap<>();
        for (var request : requests) {
            validate(request);
            if (!Objects.equals(first.userId(), request.userId()) || !Objects.equals(first.tenantId(), request.tenantId()))
                throw new IllegalArgumentException("Media grant batch must belong to one user and tenant");
            unique.merge(request.objectKey(), request, (old, next) -> next.video() ? next : old);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Map<String, MediaDeliveryGrantEntity> existing = new HashMap<>();
        store.findAll(first.userId(), unique.keySet().stream().map(MediaDeliveryGrantService::objectKeyHash).toList())
            .forEach(entity -> existing.put(entity.objectKeyHash, entity));
        Map<String, DeliveryGrant> result = new HashMap<>();
        for (var request : unique.values()) {
            var entity = existing.get(objectKeyHash(request.objectKey()));
            long threshold = request.video() ? properties.getVideoRenewalThresholdSeconds() : 0;
            if (entity != null && entity.expiryInstant().isAfter(now.plusSeconds(threshold))) {
                result.put(request.objectKey(), new DeliveryGrant(signer.sign(request.objectKey(), entity.expiryInstant()), entity.expiryInstant()));
            } else {
                // Runs inside this transaction and rechecks under the existing row lock.
                result.put(request.objectKey(), issue(request));
            }
        }
        return result;
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
