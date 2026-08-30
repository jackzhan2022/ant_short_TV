package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VideoAnalysisNormalizerTest {
    private final VideoAnalysisNormalizer normalizer = new VideoAnalysisNormalizer(new ObjectMapper());

    @Test
    void acceptsMarkdownScreenplayWithConsecutiveScenesAndVoiceCues() {
        VideoAnalysis analysis = normalizer.normalize(protocol("""
            # 第1集：门缝里的阴谋

            ## 1-1 夜 内 风崖庄园走廊

            出场人物：Serena、Rowan

            雨夜。Serena靠近虚掩的房门。

            SERENA
            （OS）
            Rowan，你怎么能这样……

            ## 1-2 夜 内 风崖庄园房间

            出场人物：Rowan、Mirabel

            ROWAN
            （压低声音）
            谁在外面？

            ——本集完
            """), 1);

        assertThat(analysis.script()).startsWith("# 第1集：门缝里的阴谋");
        assertThat(analysis.script()).contains("（OS）", "## 1-2 夜 内 风崖庄园房间");
        assertThat(analysis.normalizedJson()).contains("\"script\"");
    }

    @Test
    void acceptsVoiceOverCue() {
        assertThat(normalizer.normalize(protocol(validScript(3).replace(
            "剧情继续。", "旁白\n（VO）\n三年前，一切从这里开始。")), 3).script()).contains("（VO）");
    }

    @Test
    void rejectsMarkdownFenceAroundTheProtocol() {
        String fenced = "```json\n" + protocol(validScript(2)) + "\n```";
        assertInvalid(fenced, 2, "代码块");
    }

    @Test
    void rejectsMissingOrBlankScript() {
        assertThatThrownBy(() -> normalizer.normalize("{\"characters\":[]}", 1))
            .isInstanceOf(VideoAnalysisParseException.class).hasMessageContaining("script");
        assertThatThrownBy(() -> normalizer.normalize("{\"script\":\"   \"}", 1))
            .isInstanceOf(VideoAnalysisParseException.class).hasMessageContaining("script");
    }

    @Test
    void rejectsMismatchedEpisodeNumber() {
        assertInvalid(protocol(validScript(2)), 1, "集标题");
    }

    @Test
    void rejectsMissingOrSkippedSceneNumbers() {
        assertInvalid(protocol(validScript(1).replace("## 1-1", "场景一")), 1, "场景头");
        String skipped = validScript(1).replace("——本集完", """
            ## 1-3 夜 外 庭院

            出场人物：A

            继续。

            ——本集完
            """);
        assertInvalid(protocol(skipped), 1, "连续");
    }

    @Test
    void rejectsMissingCastBodyOrEndMarker() {
        assertInvalid(protocol(validScript(1).replace("出场人物：A、B", "人物：A、B")), 1, "出场人物");
        assertInvalid(protocol(validScript(1).replace("剧情继续。", "")), 1, "正文");
        assertInvalid(protocol(validScript(1).replace("——本集完", "")), 1, "本集完");
    }

    @Test
    void rejectsInvalidJsonAndOversizedContent() {
        assertThatThrownBy(() -> normalizer.normalize("not-json", 1))
            .isInstanceOf(VideoAnalysisParseException.class).hasMessageContaining("合法 JSON");
        String oversized = validScript(1).replace("剧情继续。", "剧".repeat(200_001));
        assertInvalid(protocol(oversized), 1, "长度");
    }

    private void assertInvalid(String response, int episodeNo, String message) {
        assertThatThrownBy(() -> normalizer.normalize(response, episodeNo))
            .isInstanceOf(VideoAnalysisParseException.class)
            .hasMessageContaining(message);
    }

    private String protocol(String script) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of("script", script));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String validScript(int episodeNo) {
        return """
            # 第%d集：测试标题

            ## %d-1 日 外 庭院

            出场人物：A、B

            剧情继续。

            ——本集完
            """.formatted(episodeNo, episodeNo);
    }
}
