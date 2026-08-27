package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VideoAnalysisNormalizerTest {

    private final VideoAnalysisNormalizer normalizer = new VideoAnalysisNormalizer(new ObjectMapper());

    @Test
    void acceptsDirectScreenplayAndKeepsNormalizedJson() {
        String response = """
            {
              "script":"第1集：[天台对峙]\\n场景：夜 外 天台\\n结尾钩子：林晚握紧录音笔。"
            }
            """;

        VideoAnalysis analysis = normalizer.normalize(response);

        assertThat(analysis.script()).contains("第1集：[天台对峙]");
        assertThat(analysis.normalizedJson()).contains("\"script\"");
    }

    @Test
    void rejectsTransportSuccessWhenScriptIsMissing() {
        String response = """
            {
              "characters":[]
            }
            """;

        assertThatThrownBy(() -> normalizer.normalize(response))
            .isInstanceOf(VideoAnalysisParseException.class)
            .hasMessageContaining("script");
    }

    @Test
    void rejectsTransportSuccessWhenScriptIsBlank() {
        assertThatThrownBy(() -> normalizer.normalize("{\"script\":\"   \"}"))
            .isInstanceOf(VideoAnalysisParseException.class)
            .hasMessageContaining("script");
    }
}
