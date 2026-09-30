package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaObjectRegistryTest {
    private InMemoryMediaObjectStore store;
    private MediaObjectRegistry registry;
    private MediaObjectIdentity identity;

    @BeforeEach
    void setUp() {
        store = new InMemoryMediaObjectStore();
        registry = new MediaObjectRegistry(store, new ObjectStorageKeyFactory());
        identity = new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 44L, "result-44");
    }

    @Test
    void persistsVerifiedOriginalMetadata() {
        StoredObject original = new StoredObject(
            "materials/11/22/images/202609/44/result-44/original.png",
            4096L,
            "image/png",
            "etag-original",
            "INTELLIGENT_TIERING"
        );

        RegisteredMediaObject registered = registry.registerOriginal(
            identity, original, "sha256-original", 1200, 800
        );

        assertThat(registered.id()).isEqualTo(1L);
        MediaObjectEntity persisted = store.current;
        assertThat(persisted.tenantId).isEqualTo(11L);
        assertThat(persisted.projectId).isEqualTo(22L);
        assertThat(persisted.assetType).isEqualTo("AI_IMAGE_RESULT");
        assertThat(persisted.assetId).isEqualTo(44L);
        assertThat(persisted.versionId).isEqualTo("result-44");
        assertThat(persisted.renditionType).isEqualTo("ORIGINAL");
        assertThat(persisted.objectKey).isEqualTo(original.key());
        assertThat(persisted.mimeType).isEqualTo("image/png");
        assertThat(persisted.fileSize).isEqualTo(4096L);
        assertThat(persisted.etag).isEqualTo("etag-original");
        assertThat(persisted.checksum).isEqualTo("sha256-original");
        assertThat(persisted.storageClass).isEqualTo("INTELLIGENT_TIERING");
        assertThat(persisted.width).isEqualTo(1200);
        assertThat(persisted.height).isEqualTo(800);
        assertThat(persisted.status).isEqualTo("READY");
    }

    @Test
    void persistsPendingRenditionKeyWithoutInventingMetadata() {
        String outputKey = "materials/11/22/images/202609/44/result-44/derived/display.png";

        registry.registerPendingRendition(
            identity, "DISPLAY", outputKey, "image/png", "INTELLIGENT_TIERING"
        );

        assertThat(store.current.renditionType).isEqualTo("DISPLAY");
        assertThat(store.current.objectKey).isEqualTo(outputKey);
        assertThat(store.current.mimeType).isEqualTo("image/png");
        assertThat(store.current.fileSize).isZero();
        assertThat(store.current.status).isEqualTo("PENDING");
    }

    @Test
    void returnsExistingRegistrationForTheSameImmutableObject() {
        StoredObject original = new StoredObject(
            "materials/11/22/images/202609/44/result-44/original.png",
            4096L,
            "image/png",
            "etag-original",
            "INTELLIGENT_TIERING"
        );

        RegisteredMediaObject first = registry.registerOriginal(identity, original, null, 1200, 800);
        RegisteredMediaObject second = registry.registerOriginal(identity, original, null, 1200, 800);

        assertThat(second).isEqualTo(first);
        assertThat(store.insertCalls).isEqualTo(1);
    }

    @Test
    void rejectsReplacingAnImmutableRegistrationWithAnotherKey() {
        registry.registerPendingRendition(
            identity,
            "DISPLAY",
            "materials/11/22/images/202609/44/result-44/derived/display.png",
            "image/png",
            "INTELLIGENT_TIERING"
        );

        assertThatThrownBy(() -> registry.registerPendingRendition(
            identity,
            "DISPLAY",
            "materials/11/22/images/202609/44/result-45/derived/display.png",
            "image/png",
            "INTELLIGENT_TIERING"
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("不可变媒体对象");
    }

    @Test
    void rejectsProviderOrSignedUrlsAsDurableObjectKeys() {
        StoredObject signedUrl = new StoredObject(
            "https://antvcdn.aixmax.cn/original.png?sign=secret&t=123",
            4096L,
            "image/png",
            "etag-original",
            "INTELLIGENT_TIERING"
        );

        assertThatThrownBy(() -> registry.registerOriginal(identity, signedUrl, null, 1200, 800))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("对象键不合法");
        assertThat(store.insertCalls).isZero();
    }

    private static final class InMemoryMediaObjectStore extends MediaObjectStore {
        private final Map<String, MediaObjectEntity> objects = new HashMap<>();
        private MediaObjectEntity current;
        private int insertCalls;

        @Override
        MediaObjectEntity find(MediaObjectIdentity identity, String renditionType) {
            return objects.get(key(identity, renditionType));
        }

        @Override
        void insert(MediaObjectEntity entity) {
            insertCalls++;
            entity.id = (long) insertCalls;
            current = entity;
            objects.put(key(entity.identity(), entity.renditionType), entity);
        }

        @Override
        boolean retryFailed(Long id) {
            if (current == null || !current.id.equals(id) || !"FAILED".equals(current.status)) {
                return false;
            }
            current.status = "PENDING";
            current.errorMessage = null;
            return true;
        }

        @Override
        void retire(MediaObjectIdentity identity) {
            objects.values().stream()
                .filter(entity -> entity.identity().equals(identity))
                .forEach(entity -> {
                    entity.status = "RETIRED";
                    entity.errorMessage = "Business result discarded";
                });
        }

        @Override
        void ready(Long id, long size, String eTag, String mimeType, int width, int height) {
        }

        @Override
        void failed(Long id, String message) {
        }

        private String key(MediaObjectIdentity identity, String renditionType) {
            return identity.tenantId() + ":" + identity.assetType() + ":" + identity.assetId()
                + ":" + identity.versionId() + ":" + renditionType;
        }
    }
}
