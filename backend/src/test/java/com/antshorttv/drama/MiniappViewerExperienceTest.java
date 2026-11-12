package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.StoredObject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
class MiniappViewerExperienceTest {
    @Autowired DramaLibraryService library;
    @Autowired MiniappViewerService viewers;
    @Autowired MiniappMapper apps;
    @Autowired LibraryEpisodeMapper episodes;
    @MockBean ObjectStorageService storage;

    @Test @Transactional void catalogAndProgressUsePublishedDirectoryAndStableViewerIdentity() {
        Miniapp app = new Miniapp(); app.tenantId = 1L; app.appid = "wx0000000000000177";
        app.name = "看剧测试"; app.enabled = true; app.bindingType = "MANUAL"; app.createdAt = LocalDateTime.now(); apps.insert(app);
        Miniapp other = new Miniapp(); other.tenantId = 1L; other.appid = "wx0000000000000178";
        other.name = "隔离应用"; other.enabled = true; other.bindingType = "MANUAL"; other.createdAt = LocalDateTime.now(); apps.insert(other);
        Drama drama = library.create(1L, new DramaRequest("春山来信", "简介", "https://cdn.example.com/cover.jpg", "都市", "ONGOING", 5));
        Episode one = episode(drama.id, 1), three = episode(drama.id, 3), five = episode(drama.id, 5);
        library.publish(1L, app.id, drama.id, new PublishRequest(List.of(five.id, one.id, three.id), 7, true));
        Drama listed = library.list(1L).stream().filter(d -> d.id.equals(drama.id)).findFirst().orElseThrow();
        assertEquals(3L, listed.episodeCount); assertEquals(1L, listed.publishedAppCount);
        var page = viewers.catalog(app, 1, 20, "春山", "都市");
        assertEquals(1, page.total()); assertEquals(List.of("都市"), page.categories());
        assertEquals(0, viewers.catalog(app, 1, 20, "不存在", null).total());
        assertEquals(0, viewers.catalog(app, 1, 20, "%", null).total());
        assertTrue(viewers.catalog(other, 1, 20, null, null).items().isEmpty());
        assertEquals(List.of(1, 3, 5), viewers.detail(app, drama.id).episodes().stream().map(e -> e.episodeNo).toList());
        Drama priority = library.create(1L, new DramaRequest("优先展示", "简介", "https://cdn.example.com/priority.jpg", "都市", "ONGOING", 5));
        Episode priorityEpisode = episode(priority.id, 1);
        library.publish(1L, app.id, priority.id, new PublishRequest(List.of(priorityEpisode.id), -2, false));
        assertEquals(priority.id, viewers.catalog(app, 1, 20, null, null).items().get(0).dramaId);
        Viewer alice = new Viewer(app, "alice"), bob = new Viewer(app, "bob");
        viewers.saveProgress(alice, drama.id, new ProgressRequest(three.id, 6.0, 20.0, false, 2L, "play-a"));
        viewers.saveProgress(alice, drama.id, new ProgressRequest(one.id, 1.0, 20.0, false, 1L, "play-a"));
        assertEquals(three.id, viewers.history(alice).get(0).episodeId);
        assertEquals(6, viewers.history(new Viewer(app, "alice")).get(0).positionSeconds);
        assertTrue(viewers.history(bob).isEmpty());
        assertTrue(viewers.history(new Viewer(other, "alice")).isEmpty());
        viewers.saveProgress(alice, drama.id, new ProgressRequest(five.id, 200.0, 999.0, false, 3L, "play-a"));
        assertEquals(20, viewers.history(alice).get(0).positionSeconds);
        viewers.clearHistory(bob); assertEquals(1, viewers.history(alice).size());
        String version = page.version();
        library.unpublish(1L, app.id, drama.id);
        assertNotEquals(version, viewers.catalog(app, 1, 20, null, null).version());
        assertTrue(viewers.history(alice).isEmpty());
        assertThrows(com.antshorttv.common.BusinessException.class,
            () -> viewers.saveProgress(alice, drama.id, new ProgressRequest(one.id, 1.0, 20.0, false, 4L, "play-a")));
    }

    private Episode episode(Long dramaId, int no) {
        Episode episode = new Episode(); episode.tenantId = 1L; episode.dramaId = dramaId; episode.episodeNo = no;
        episode.title = "第" + no + "集"; episode.sourceType = "LOCAL_UPLOAD"; episode.storagePath = "materials/1/drama/" + no + ".mp4";
        episode.fileSize = 100L; episode.durationSeconds = 20.0; episode.width = 720; episode.height = 1280; episode.createdAt = LocalDateTime.now();
        episodes.insert(episode);
        when(storage.metadata(episode.storagePath)).thenReturn(new StoredObject(episode.storagePath, 100L, "video/mp4", "etag", null));
        return episode;
    }
}
