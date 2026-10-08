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
}
