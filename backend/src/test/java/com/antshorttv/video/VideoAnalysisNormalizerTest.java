package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VideoAnalysisNormalizerTest {

    private final VideoAnalysisNormalizer normalizer = new VideoAnalysisNormalizer(new ObjectMapper());

    @Test
    void acceptsCompleteStructuredAnalysisAndKeepsNormalizedJson() {
        String response = """
            {
              "characters":[{"name":"林晚","roleType":"LEAD"}],
              "scenes":[{"name":"天台","sceneType":"EXTERIOR"}],
              "props":[{"name":"录音笔","propType":"KEY_PROP"}],
              "timeline":[{"time":"00:01","event":"林晚拿出录音笔"}],
              "dialogue":[{"speaker":"林晚","text":"你终于来了"}],
              "actions":[{"actor":"林晚","action":"转身"}],
              "emotions":[{"character":"林晚","emotion":"冷静"}]
            }
            """;

        VideoAnalysis analysis = normalizer.normalize(response);

        assertThat(analysis.characters()).hasSize(1);
        assertThat(analysis.normalizedJson()).contains("\"林晚\"");
    }

    @Test
    void rejectsTransportSuccessWhenRequiredAnalysisFieldIsMissing() {
        String response = """
            {
              "characters":[],
              "scenes":[],
              "props":[],
              "timeline":[],
              "dialogue":[],
              "actions":[]
            }
            """;

        assertThatThrownBy(() -> normalizer.normalize(response))
            .isInstanceOf(VideoAnalysisParseException.class)
            .hasMessageContaining("emotions");
    }
}
