package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaUploadSessionServiceTest {

    @Test
    void createsTenantScopedSessionAndReturnsTemporaryCredentials() {
        InMemoryStore store = new InMemoryStore();
        TemporaryCosCredentials credentials = new TemporaryCosCredentials(
            "tmp-id", "tmp-key", "token", 1_700_003_600L, "request-1"
        );
        MediaUploadSessionService service = service(store, credentials);

        MediaUploadSession session = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "episode.mp4", "video/mp4", null)
        );

        assertThat(session.objectKey()).startsWith("uploads/11/").endsWith("/source.mp4");
        assertThat(session.credentials()).isEqualTo(credentials);
        assertThat(store.current.status).isEqualTo("PENDING");
    }

    @Test
    void completesOnlyAfterHeadMetadataMatchesSession() {
        InMemoryStore store = new InMemoryStore();
        TemporaryCosCredentials credentials = new TemporaryCosCredentials(
            "tmp-id", "tmp-key", "token", 1_700_003_600L, "request-1"
        );
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(store, credentials, storage);
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, 22L, 33L, "image.png", "image/png", 3L)
        );
        when(storage.metadata(created.objectKey())).thenReturn(
            new StoredObject(created.objectKey(), 3, "image/png", "etag-1", "INTELLIGENT_TIERING")
        );

        VerifiedMediaUpload completed = service.complete(33L, created.sessionToken());

        assertThat(completed.objectKey()).isEqualTo(created.objectKey());
        assertThat(completed.eTag()).isEqualTo("etag-1");
        assertThat(store.current.status).isEqualTo("COMPLETED");
    }

    @Test
    void rejectsCompletionForAnotherUserOrMismatchedSize() {
        InMemoryStore store = new InMemoryStore();
        TemporaryCosCredentials credentials = new TemporaryCosCredentials(
            "tmp-id", "tmp-key", "token", 1_700_003_600L, "request-1"
        );
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(store, credentials, storage);
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "image.png", "image/png", 3L)
        );

        assertThatThrownBy(() -> service.complete(44L, created.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class);

        when(storage.metadata(created.objectKey())).thenReturn(
            new StoredObject(created.objectKey(), 4, "image/png", "etag-1", "INTELLIGENT_TIERING")
        );
        assertThatThrownBy(() -> service.complete(33L, created.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompletedUploadFromAnotherTenant() {
        InMemoryStore store = new InMemoryStore();
        TemporaryCosCredentials credentials = new TemporaryCosCredentials(
            "tmp-id", "tmp-key", "token", 1_700_003_600L, "request-1"
        );
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(store, credentials, storage);
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "image.png", "image/png", 3L)
        );
        when(storage.metadata(created.objectKey())).thenReturn(
            new StoredObject(created.objectKey(), 3, "image/png", "etag-1", "INTELLIGENT_TIERING")
        );
        service.complete(33L, created.sessionToken());

        assertThatThrownBy(() -> service.requireCompleted(33L, 12L, created.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上传会话不存在");
    }

    private MediaUploadSessionService service(InMemoryStore store, TemporaryCosCredentials credentials) {
        return service(store, credentials, mock(ObjectStorageService.class));
    }

    private MediaUploadSessionService service(
        InMemoryStore store,
        TemporaryCosCredentials credentials,
        ObjectStorageService storage
    ) {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        return new MediaUploadSessionService(
            store,
            new TemporaryCosCredentialIssuer() {
                @Override
                public TemporaryCosCredentials issue(String name, String policy, long durationSeconds) {
                    return credentials;
                }
            },
            new CosUploadPolicyFactory(properties, new ObjectMapper()),
            new ObjectStorageKeyFactory(),
            storage,
            properties,
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private static final class InMemoryStore extends MediaUploadSessionStore {
        private final Map<String, MediaUploadSessionEntity> rows = new HashMap<>();
        private MediaUploadSessionEntity current;

        @Override
        public void insert(MediaUploadSessionEntity entity) {
            rows.put(entity.sessionToken, entity);
            current = entity;
        }

        @Override
        public MediaUploadSessionEntity find(String token) {
            return rows.get(token);
        }

        @Override
        public void update(MediaUploadSessionEntity entity) {
            rows.put(entity.sessionToken, entity);
            current = entity;
        }
    }
}
