package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoryboardCompositionResponseTest {
    @Test
    void keepsTheSelectedComposedVideoWhenAttachingAssetReferences() {
        StoryboardEntity entity = new StoryboardEntity();
        entity.id = 31L;
        entity.projectId = 44L;
        entity.currentVideoUrl = "https://example.com/raw.mp4";
        entity.currentShotResultId = 7301L;
        entity.currentShotVideoUrl = "https://example.com/composed.mp4";

        var response = new ObjectMapper().valueToTree(
            StoryboardResponse.from(entity).withAssetReferences(List.of()));

        assertThat(response.path("currentVideoUrl").asText()).isEqualTo(entity.currentVideoUrl);
        assertThat(response.path("currentShotResultId").asLong()).isEqualTo(7301L);
        assertThat(response.path("currentShotVideoUrl").asText()).isEqualTo(entity.currentShotVideoUrl);
    }
    @Test
    void usesTheBoundResultIdsForBrowserPlaybackInsteadOfStorageDirectories() {
        StoryboardEntity entity = new StoryboardEntity();
        entity.id = 1041L;
        entity.projectId = 44L;
        entity.currentVideoResultId = 1L;
        entity.currentVideoUrl = "/materials/1/44/videos/202610/2/result/original.mp4";
        entity.currentShotResultId = 7301L;
        entity.currentShotVideoUrl = "/materials/1/44/composed/999/result.mp4";

        var browser = StoryboardResponse.from(entity).forBrowser().withAssetReferences(List.of());

        assertThat(browser.currentVideoUrl()).isEqualTo("/api/projects/44/ai-video-results/1/playback");
        assertThat(browser.currentShotVideoUrl()).isEqualTo("/api/projects/44/shot-compose-results/7301/playback");
        assertThat(browser.currentVideoResultId()).isEqualTo(1L);
        assertThat(browser.currentShotResultId()).isEqualTo(7301L);
        assertThat(entity.currentVideoUrl).startsWith("/materials/");
    }

    @Test
    void retainsLegacyExternalVideosWithoutResultIds() {
        StoryboardEntity entity = new StoryboardEntity();
        entity.projectId = 44L;
        entity.currentVideoUrl = "https://example.com/legacy.mp4";
        var browser = StoryboardResponse.from(entity).forBrowser();
        assertThat(browser.currentVideoUrl()).isEqualTo(entity.currentVideoUrl);
        assertThat(browser.currentShotVideoUrl()).isNull();
    }

    @Test
    void doesNotCreatePlayableUrlsForMissingVideoContent() {
        StoryboardEntity entity = new StoryboardEntity();
        entity.projectId = 44L;
        entity.currentVideoResultId = 1L;
        entity.currentShotResultId = 7301L;
        entity.currentShotVideoUrl = "";
        var browser = StoryboardResponse.from(entity).forBrowser();
        assertThat(browser.currentVideoUrl()).isNull();
        assertThat(browser.currentShotVideoUrl()).isEmpty();
    }
}
