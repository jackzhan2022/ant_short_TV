package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.shot.EpisodeVideoVersionEntity;
import com.antshorttv.storage.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DramaLibraryServiceTest {
    DramaMapper dramas = mock(DramaMapper.class);
    LibraryEpisodeMapper episodes = mock(LibraryEpisodeMapper.class);
    MiniappMapper apps = mock(MiniappMapper.class);
    ReleaseMapper releases = mock(ReleaseMapper.class);
    ReleaseEpisodeMapper items = mock(ReleaseEpisodeMapper.class);
    DramaSourceMapper sources = mock(DramaSourceMapper.class);
    MediaUploadSessionService uploads = mock(MediaUploadSessionService.class);
    ObjectStorageService storage = mock(ObjectStorageService.class);
    ProjectPermissionGuard projects = mock(ProjectPermissionGuard.class);
    org.springframework.mock.env.MockEnvironment environment = new org.springframework.mock.env.MockEnvironment();
    DramaLibraryService service;

    @BeforeEach void setup() {
        service = new DramaLibraryService(dramas, episodes, apps, releases, items, sources, uploads,
            storage, new ObjectStorageKeyFactory(), projects, new MiniappOwnership(environment));
        Drama drama = new Drama(); drama.id = 10L; drama.tenantId = 1L; drama.title = "剧集";
        when(dramas.lock(1L, 10L)).thenReturn(drama);
        when(episodes.selectCount(any())).thenReturn(0L);
    }

    @Test void localUploadCopiesVerifiedOriginal() {
        String key = "materials/1/uploads/202610/session/v1/original.mp4";
        when(uploads.requireCompleted(7L, 1L, null, "session")).thenReturn(new VerifiedMediaUpload("session", key, "video/mp4", 50, "etag"));
        when(storage.metadata(key)).thenReturn(new StoredObject(key, 50, "video/mp4", "etag", "INTELLIGENT_TIERING"));
        when(storage.copyCompletedUploadOriginal(any(), anyString())).thenAnswer(call ->
            new StoredObject(call.getArgument(1), 50, "video/mp4", "etag", "INTELLIGENT_TIERING"));
        Episode episode = service.importEpisode(1L, 7L, 10L, new EpisodeRequest(1, "第一集", "session", null, null));
        assertTrue(episode.storagePath.startsWith("materials/1/drama/"));
        assertEquals("LOCAL_UPLOAD", episode.sourceType);
        verify(uploads).requireCompleted(7L, 1L, null, "session");
        verify(episodes).insert(episode);
    }

    @Test void bindingRejectsUnconfiguredAndCrossTeamAppidBeforeInsert() {
        BindingRequest body = new BindingRequest("wx1234567890abcdef", "应用");
        environment.setProperty("miniapp.apps." + body.appid() + ".secret", "test-secret");
        assertThrows(BusinessException.class, () -> service.bind(1L, body));
        environment.setProperty("miniapp.apps." + body.appid() + ".tenant-id", "2");
        assertThrows(BusinessException.class, () -> service.bind(1L, body));
        verifyNoInteractions(apps);
        environment.setProperty("miniapp.apps." + body.appid() + ".tenant-id", "1");
        assertEquals(1L, service.bind(1L, body).tenantId);
        verify(apps).insert(any(Miniapp.class));
    }

    @Test void projectScopeUploadCannotBeUsedAsLocalSource() {
        when(uploads.requireCompleted(7L, 1L, null, "session"))
            .thenThrow(new IllegalArgumentException("上传会话范围不匹配"));
        assertThrows(IllegalArgumentException.class, () -> service.importEpisode(1L, 7L, 10L,
            new EpisodeRequest(1, "集", "session", null, null)));
        verifyNoInteractions(storage);
        verify(episodes, never()).insert(any(Episode.class));
    }

    @Test void crossTeamDramaCannotImport() {
        assertThrows(BusinessException.class, () -> service.importEpisode(2L, 7L, 10L,
            new EpisodeRequest(1, "集", "session", null, null)));
        verifyNoInteractions(uploads, storage, sources);
    }

    @Test void mixedSourcesRejected() {
        assertThrows(BusinessException.class, () -> service.importEpisode(1L, 7L, 10L,
            new EpisodeRequest(1, "集", "session", 20L, 30L)));
        verifyNoInteractions(storage, sources);
    }

    @Test void projectSourceRequiresDownloadPermissionAndOwnedActiveVersion() {
        EpisodeVideoVersionEntity source = new EpisodeVideoVersionEntity(); source.id = 30L;
        source.storagePath = "materials/1/20/episode/202610/30/v1/original.mp4";
        when(sources.lock(1L, 20L, 30L)).thenReturn(source);
        when(storage.metadata(source.storagePath)).thenReturn(new StoredObject(source.storagePath, 42, "video/mp4", "etag", "INTELLIGENT_TIERING"));
        Episode episode = service.importEpisode(1L, 7L, 10L, new EpisodeRequest(1, "集", null, 20L, 30L));
        verify(projects).require(1L, 20L, "EPISODE_VERSION:DOWNLOAD");
        assertEquals(30L, episode.sourceVersionId);
    }

    @Test void projectForeignStorageRejected() {
        EpisodeVideoVersionEntity source = new EpisodeVideoVersionEntity(); source.id = 30L;
        source.storagePath = "materials/2/20/episode/original.mp4";
        when(sources.lock(1L, 20L, 30L)).thenReturn(source);
        assertThrows(BusinessException.class, () -> service.importEpisode(1L, 7L, 10L,
            new EpisodeRequest(1, "集", null, 20L, 30L)));
        verifyNoInteractions(storage);
    }

    @Test void nonVideoUploadRejected() {
        when(uploads.requireCompleted(7L, 1L, null, "session")).thenReturn(new VerifiedMediaUpload("session", "image", "image/png", 50, "etag"));
        when(storage.metadata("image")).thenReturn(new StoredObject("image", 50, "image/png", "etag", "INTELLIGENT_TIERING"));
        assertThrows(BusinessException.class, () -> service.importEpisode(1L, 7L, 10L, new EpisodeRequest(1, "集", "session", null, null)));
        verify(storage, never()).copyCompletedUploadOriginal(any(), anyString());
    }

    @Test void releasePinsSelectedEpisodeAndMetadata() {
        Miniapp app = new Miniapp(); app.id = 40L; app.enabled = true;
        when(apps.lock(1L, 40L)).thenReturn(app);
        Episode episode = new Episode(); episode.id = 50L; episode.episodeNo = 1; episode.title = "固定标题";
        episode.storagePath = "materials/1/video/original.mp4"; episode.sourceVersionId = 60L;
        when(episodes.selectList(any())).thenReturn(List.of(episode));
        when(storage.metadata(episode.storagePath)).thenReturn(new StoredObject(episode.storagePath, 50, "video/mp4", "etag", "INTELLIGENT_TIERING"));
        doAnswer(call -> { ((Release)call.getArgument(0)).id = 70L; return 1; }).when(releases).insert(any(Release.class));
        service.publish(1L, 40L, 10L, new PublishRequest(List.of(50L)));
        var capture = org.mockito.ArgumentCaptor.forClass(ReleaseEpisode.class);
        verify(items).insert(capture.capture());
        assertEquals(70L, capture.getValue().releaseId);
        assertEquals(60L, capture.getValue().sourceVersionId);
        assertEquals(episode.storagePath, capture.getValue().storagePath);
    }

    @Test void publishedDramaCannotBeDeleted() {
        when(releases.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.delete(1L, 10L));
        verify(dramas, never()).updateById(any(Drama.class));
    }

    @Test void unpublishedOrOtherAppCannotPlay() {
        Miniapp app = new Miniapp(); app.id = 40L; app.tenantId = 1L;
        assertThrows(BusinessException.class, () -> service.play(app, 10L, 50L));
        verifyNoInteractions(storage);
    }

    @Test void playbackSignsOnlySnapshotPathForFiveMinutes() {
        Miniapp app = new Miniapp(); app.id = 40L; app.tenantId = 1L;
        Release release = new Release(); release.id = 70L;
        when(releases.selectOne(any(Wrapper.class))).thenReturn(release);
        ReleaseEpisode item = new ReleaseEpisode(); item.storagePath = "materials/1/fixed/original.mp4";
        when(items.selectOne(any(Wrapper.class))).thenReturn(item);
        when(storage.cdnUrl(eq(item.storagePath), any())).thenReturn("signed");
        PlayResponse result = service.play(app, 10L, 50L);
        assertEquals("signed", result.url());
        assertTrue(result.expiresAt().isBefore(java.time.Instant.now().plusSeconds(301)));
        verify(storage).cdnUrl(eq(item.storagePath), eq(result.expiresAt()));
    }
}
