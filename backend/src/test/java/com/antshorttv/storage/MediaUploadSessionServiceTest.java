package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaUploadSessionServiceTest {

    @Test
    void createsTenantScopedSessionWithoutReturningCloudCredentials() {
        InMemoryStore store = new InMemoryStore();
        MediaUploadSessionService service = service(store, mock(CosUploadRequestAuthorizer.class));

        MediaUploadSession session = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "episode.mp4", "video/mp4", null)
        );

        assertThat(session.objectKey()).startsWith("uploads/11/").endsWith("/source.mp4");
        assertThat(session).hasFieldOrPropertyWithValue("storageClass", "INTELLIGENT_TIERING");
        assertThat(store.current.status).isEqualTo("PENDING");
    }

    @Test
    void completesOnlyAfterHeadMetadataMatchesSession() {
        InMemoryStore store = new InMemoryStore();
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(
            store, mock(CosUploadRequestAuthorizer.class), storage
        );
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, 22L, 33L, "image.png", "image/png", 3L)
        );
        when(storage.metadata(created.objectKey())).thenReturn(
            new StoredObject(created.objectKey(), 3, "image/png", "etag-1", "INTELLIGENT_TIERING")
        );
        when(storage.promoteVerifiedUpload(any(StoredObject.class), anyString()))
            .thenAnswer(invocation -> new StoredObject(
                invocation.getArgument(1), 3, "image/png", "etag-1", "INTELLIGENT_TIERING"
            ));

        VerifiedMediaUpload completed = service.complete(33L, created.sessionToken());

        assertThat(completed.objectKey())
            .isEqualTo("materials/11/22/uploads/202311/" + created.sessionToken()
                + "/v1/original.png");
        assertThat(completed.objectKey()).doesNotStartWith("uploads/");
        assertThat(completed.eTag()).isEqualTo("etag-1");
        assertThat(store.current.status).isEqualTo("COMPLETED");
        assertThat(store.current.objectKey).isEqualTo(completed.objectKey());
    }

    @Test
    void rejectsCompletionForAnotherUserOrMismatchedSize() {
        InMemoryStore store = new InMemoryStore();
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(
            store, mock(CosUploadRequestAuthorizer.class), storage
        );
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
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(
            store, mock(CosUploadRequestAuthorizer.class), storage
        );
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "image.png", "image/png", 3L)
        );
        when(storage.metadata(created.objectKey())).thenReturn(
            new StoredObject(created.objectKey(), 3, "image/png", "etag-1", "INTELLIGENT_TIERING")
        );
        when(storage.promoteVerifiedUpload(any(StoredObject.class), anyString()))
            .thenAnswer(invocation -> new StoredObject(
                invocation.getArgument(1), 3, "image/png", "etag-1", "INTELLIGENT_TIERING"
            ));
        service.complete(33L, created.sessionToken());

        assertThatThrownBy(() -> service.requireCompleted(33L, 12L, created.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上传会话不存在");
    }

    @Test
    void rejectsCompletionWhenCosDidNotUseConfiguredStorageClass() {
        InMemoryStore store = new InMemoryStore();
        ObjectStorageService storage = mock(ObjectStorageService.class);
        MediaUploadSessionService service = service(
            store, mock(CosUploadRequestAuthorizer.class), storage
        );
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "image.png", "image/png", 3L)
        );
        when(storage.metadata(created.objectKey())).thenReturn(
            new StoredObject(created.objectKey(), 3, "image/png", "etag-1", "STANDARD")
        );

        assertThatThrownBy(() -> service.complete(33L, created.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("存储类型");
    }

    @Test
    void authorizesOnlyTheOwnedPendingSessionObject() {
        InMemoryStore store = new InMemoryStore();
        CosUploadRequestAuthorizer authorizer = mock(CosUploadRequestAuthorizer.class);
        MediaUploadSessionService service = service(store, authorizer);
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "image.png", "image/png", 3L)
        );
        CosUploadAuthorizationRequest request = new CosUploadAuthorizationRequest(
            "PUT", "/" + created.objectKey(), Map.of(), Map.of("host", "cos.example")
        );
        CosUploadAuthorization expected = new CosUploadAuthorization(
            "authorization", "security-token", 1_700_000_300L
        );
        when(authorizer.authorize(created.objectKey(), request)).thenReturn(expected);

        assertThat(service.authorize(33L, created.sessionToken(), request)).isEqualTo(expected);
        verify(authorizer).authorize(created.objectKey(), request);

        assertThatThrownBy(() -> service.authorize(44L, created.sessionToken(), request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上传会话不存在");
    }

    @Test
    void authorizesSdkMultipartLookupOnlyForTheOwnedPendingSession() {
        InMemoryStore store = new InMemoryStore();
        COSCredentialsProvider credentials = mock(COSCredentialsProvider.class);
        when(credentials.getCredentials()).thenReturn(
            new BasicSessionCredentials("instance-id", "instance-key", "instance-token")
        );
        CosUploadRequestAuthorizer authorizer = new CosUploadRequestAuthorizer(
            credentials, new ObjectStorageProperties(),
            Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC)
        );
        MediaUploadSessionService service = service(store, authorizer);
        MediaUploadSession created = service.create(
            new CreateMediaUploadSession(11L, null, 33L, "multipart.mp4", "video/mp4", 35_403_873L)
        );
        CosUploadAuthorizationRequest request = new CosUploadAuthorizationRequest(
            "GET", "/", Map.of("uploads", "", "prefix", created.objectKey()),
            Map.of("Host", "antv-1418200553.cos.ap-guangzhou.myqcloud.com")
        );

        assertThat(assertDoesNotThrow(() -> service.authorize(33L, created.sessionToken(), request)).authorization())
            .contains("q-url-param-list=prefix;uploads");
        assertThatThrownBy(() -> service.authorize(44L, created.sessionToken(), request))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("上传会话不存在");

        service.cancel(33L, created.sessionToken());

        assertThatThrownBy(() -> service.authorize(33L, created.sessionToken(), request))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不可签名");
    }

    @Test
    void completedScopeMustMatchIncludingNullProject() {
        InMemoryStore store = new InMemoryStore();
        MediaUploadSessionService service = service(store, mock(CosUploadRequestAuthorizer.class));
        MediaUploadSession project = service.create(new CreateMediaUploadSession(11L, 22L, 33L, "video.mp4", "video/mp4", 3L));
        store.current.status = "COMPLETED"; store.current.verifiedSize = 3L;
        assertThatThrownBy(() -> service.requireCompleted(33L, 11L, null, project.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("范围");
        assertDoesNotThrow(() -> service.requireCompleted(33L, 11L, 22L, project.sessionToken()));
        MediaUploadSession local = service.create(new CreateMediaUploadSession(11L, null, 33L, "video.mp4", "video/mp4", 3L));
        assertThatThrownBy(() -> service.requireCompleted(33L, 11L, null, local.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("尚未完成");
        store.current.status = "COMPLETED"; store.current.verifiedSize = 3L;
        assertDoesNotThrow(() -> service.requireCompleted(33L, 11L, null, local.sessionToken()));
        assertThatThrownBy(() -> service.requireCompleted(33L, 12L, null, local.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.requireCompleted(44L, 11L, null, local.sessionToken()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private MediaUploadSessionService service(
        InMemoryStore store,
        CosUploadRequestAuthorizer authorizer
    ) {
        return service(store, authorizer, mock(ObjectStorageService.class));
    }

    private MediaUploadSessionService service(
        InMemoryStore store,
        CosUploadRequestAuthorizer authorizer,
        ObjectStorageService storage
    ) {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        return new MediaUploadSessionService(
            store,
            authorizer,
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
