package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.storage.StoredObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DramaExperienceTest extends DramaLibraryServiceTest {
    @Test void sameVerifiedUploadRetryReturnsExistingEpisodeWithoutRecopying() {
        Episode existing = new Episode(); existing.id = 50L; existing.episodeNo = 1; existing.title = "第一集";
        when(episodes.selectOne(any())).thenReturn(existing);
        assertSame(existing, service.importEpisode(1L, 7L, 10L, new EpisodeRequest(1, "第一集", "session", null, null)));
        verifyNoInteractions(uploads, storage);
        assertThrows(com.antshorttv.common.BusinessException.class,
            () -> service.importEpisode(1L, 7L, 10L, new EpisodeRequest(2, "第一集", "session", null, null)));
    }
    @Test void preflightRejectsIncompleteMetadataWithoutChangingPublishedRelease() {
        Miniapp app = new Miniapp(); app.id = 40L; app.enabled = true;
        when(apps.selectOne(any())).thenReturn(app);
        Drama incomplete = dramas.lock(1L, 10L); incomplete.coverUrl = null;
        when(dramas.selectOne(any())).thenReturn(incomplete);
        Episode episode = new Episode(); episode.id = 50L; episode.episodeNo = 3;
        episode.storagePath = "materials/1/drama/video.mp4";
        when(episodes.selectList(any())).thenReturn(List.of(episode));
        when(storage.metadata(episode.storagePath)).thenReturn(new StoredObject(episode.storagePath, 50, "video/mp4", "etag", null));
        var result = service.preflight(1L, 40L, 10L, new PublishRequest(List.of(50L)));
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(s -> s.contains("封面")));
        verify(releases, never()).update(any(), any());
    }

    @Test void publishPinsMetadataAndReportsChangedSnapshot() {
        Drama drama = dramas.lock(1L, 10L);
        drama.coverUrl = "https://cdn.example.com/cover.jpg"; drama.category = "都市";
        drama.completionStatus = "ONGOING"; drama.totalEpisodes = 5;
        when(dramas.selectOne(any())).thenReturn(drama);
        Miniapp app = new Miniapp(); app.id = 40L; app.enabled = true;
        when(apps.selectOne(any())).thenReturn(app); when(apps.lock(1L, 40L)).thenReturn(app);
        Episode episode = new Episode(); episode.id = 50L; episode.episodeNo = 3; episode.title = "第三集";
        episode.durationSeconds = 12.5; episode.width = 720; episode.height = 1280;
        episode.storagePath = "materials/1/drama/video.mp4";
        when(episodes.selectList(any())).thenReturn(List.of(episode));
        when(storage.metadata(episode.storagePath)).thenReturn(new StoredObject(episode.storagePath, 50, "video/mp4", "etag", null));
        Release previous = new Release(); previous.id = 69L; previous.title = "旧剧名";
        when(releases.selectOne(any())).thenReturn(previous);
        ReleaseEpisode old = new ReleaseEpisode(); old.episodeId = 50L; old.episodeNo = 3; old.title = "旧标题";
        when(items.selectList(any())).thenReturn(List.of(old));
        var result = service.preflight(1L, 40L, 10L, new PublishRequest(List.of(50L), 8, true));
        assertTrue(result.valid()); assertTrue(result.metadataChanged());
        assertEquals(List.of(3), result.changed());
        Release published = service.publish(1L, 40L, 10L, new PublishRequest(List.of(50L), 8, true));
        assertEquals(drama.coverUrl, published.coverUrl); assertEquals(8, published.sortOrder);
        var capture = org.mockito.ArgumentCaptor.forClass(ReleaseEpisode.class);
        verify(items).insert(capture.capture());
        assertEquals(12.5, capture.getValue().durationSeconds);
    }
}
