package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaDeliveryGrantServiceTest {

    @Test
    void reusesPersistedExpiryAndReturnsIdenticalUrlWithinSevenDays() {
        Instant now = Instant.parse("2026-09-29T12:00:00Z");
        InMemoryStore store = new InMemoryStore();
        MediaDeliveryGrantService service = service(store, now);
        DeliveryGrantRequest request = request(false);

        DeliveryGrant first = service.issue(request);
        DeliveryGrant second = service.issue(request);

        assertThat(second.url()).isEqualTo(first.url());
        assertThat(second.expiresAt()).isEqualTo(now.plusSeconds(604800));
        assertThat(store.inserts).isEqualTo(1);
        assertThat(store.updates).isZero();
    }

    @Test
    void renewsVideoBeforeTwoHourThreshold() {
        Instant now = Instant.parse("2026-09-29T12:00:00Z");
        InMemoryStore store = new InMemoryStore();
        DeliveryGrantRequest request = request(true);
        String hash = MediaDeliveryGrantService.objectKeyHash(request.objectKey());
        MediaDeliveryGrantEntity existing = MediaDeliveryGrantEntity.create(request, hash,
            now.plusSeconds(7199), 3, now.minusSeconds(100));
        store.insert(existing);
        store.inserts = 0;

        DeliveryGrant renewed = service(store, now).issue(request);

        assertThat(renewed.expiresAt()).isEqualTo(now.plusSeconds(604800));
        assertThat(store.updates).isOne();
        assertThat(store.current.revision).isEqualTo(4);
    }

    @Test
    void doesNotPersistSignedUrlOrSigningKey() {
        Instant now = Instant.parse("2026-09-29T12:00:00Z");
        InMemoryStore store = new InMemoryStore();

        DeliveryGrant grant = service(store, now).issue(request(false));

        assertThat(grant.url()).contains("sign=").contains("t=");
        assertThat(store.current.toString()).doesNotContain("sign=", "test-key", "antvcdn");
    }

    private MediaDeliveryGrantService service(InMemoryStore store, Instant now) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setCdnDomain("https://antvcdn.aixmax.cn");
        properties.setCdnTypeDKey("test-key");
        return new MediaDeliveryGrantService(store, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private DeliveryGrantRequest request(boolean video) {
        return new DeliveryGrantRequest(
            11L, 22L, 33L, "AI_VIDEO_RESULT", 44L, "v1", video ? "VIDEO" : "THUMBNAIL",
            "materials/11/22/videos/202609/44/v1/original.mp4", video
        );
    }

    private static final class InMemoryStore extends MediaDeliveryGrantStore {
        private final Map<String, MediaDeliveryGrantEntity> rows = new HashMap<>();
        private MediaDeliveryGrantEntity current;
        private int inserts;
        private int updates;

        @Override
        public MediaDeliveryGrantEntity find(Long userId, String objectKeyHash) {
            return rows.get(userId + ":" + objectKeyHash);
        }

        @Override
        public void insert(MediaDeliveryGrantEntity entity) {
            rows.put(entity.userId + ":" + entity.objectKeyHash, entity);
            current = entity;
            inserts++;
        }

        @Override
        public void update(MediaDeliveryGrantEntity entity) {
            rows.put(entity.userId + ":" + entity.objectKeyHash, entity);
            current = entity;
            updates++;
        }
    }
}
