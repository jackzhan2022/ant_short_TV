package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiVideoResultReferenceTest {
    @Test
    void preservesModelReferenceIndependentlyOfBrowserPlayback() throws Exception {
        var entity = new AiVideoResultEntity();
        entity.id = 1L; entity.projectId = 22L;
        entity.storagePath = "materials/11/22/videos/1/v1/original.mp4";
        entity.videoUrl = "/materials/11/22/videos/1/v1/original.mp4";
        var json = new ObjectMapper().valueToTree(AiVideoResultResponse.fromBrowser(entity));
        assertThat(json.path("videoUrl").asText()).isEqualTo("/api/projects/22/ai-video-results/1/playback");
        assertThat(json.path("referenceUrl").asText()).isEqualTo(entity.videoUrl);
        assertThat(entity.videoUrl).isEqualTo("/materials/11/22/videos/1/v1/original.mp4");
    }
}
