package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class EpisodeSplitWarningsTest {
    @Test
    void workspaceExposesMissingTitleAsAdvisoryWithoutClaimingMissingContent() {
        String source = "第六集\n甲\n第八集\n乙";
        var workspace = workspace(source, List.of(
            new ScriptEpisodeResponse(1, "六", "第六集\n甲\n"),
            new ScriptEpisodeResponse(2, "八", "第八集\n乙")));
        var json = new ObjectMapper().valueToTree(workspace);
        assertThat(json.path("episodeWarnings").isArray()).isTrue();
        assertThat(json.path("episodeWarnings").toString()).contains("SOURCE_NUMBER_GAP", "第 7 集标题");
        assertThat(json.path("episodes")).hasSize(2);
    }

    @Test
    void warnsForRepeatedAndOutOfOrderHeadingsAndDifferentBoundaries() {
        String source = "第2集\n甲\n第2集\n乙\n第1集\n丙";
        var json = new ObjectMapper().valueToTree(workspace(source,
            List.of(new ScriptEpisodeResponse(1, "合并", source))));
        assertThat(json.path("episodeWarnings").toString())
            .contains("SOURCE_NUMBER_DUPLICATE", "SOURCE_NUMBER_ORDER", "EPISODE_BOUNDARY_DIFFERENCE");
    }

    @Test
    void warnsWhenOneExplicitEpisodeIsSplitButNotForNormalOrTitlelessScripts() {
        String source = "第1集\n甲\n乙";
        var split = new ObjectMapper().valueToTree(workspace(source, List.of(
            new ScriptEpisodeResponse(1, "上", "第1集\n甲\n"),
            new ScriptEpisodeResponse(2, "下", "乙"))));
        assertThat(split.path("episodeWarnings").toString()).contains("EPISODE_BOUNDARY_DIFFERENCE");
        for (String normal : List.of("第1集\n甲", "没有集标题的正文")) {
            var json = new ObjectMapper().valueToTree(workspace(normal,
                List.of(new ScriptEpisodeResponse(1, "一", normal))));
            assertThat(json.path("episodeWarnings").isArray()).isTrue();
            assertThat(json.path("episodeWarnings")).isEmpty();
        }
    }

    @Test
    void warnsWhenBoundaryMovesWithoutChangingTheNumberOfSegments() {
        String source = "第1集\n甲\n乙\n第2集\n丙";
        var json = new ObjectMapper().valueToTree(workspace(source, List.of(
            new ScriptEpisodeResponse(1, "一", "第1集\n甲\n"),
            new ScriptEpisodeResponse(2, "二", "乙\n第2集\n丙"))));
        assertThat(json.path("episodeWarnings").toString()).contains("EPISODE_BOUNDARY_DIFFERENCE");
    }

    @Test
    void doesNotCompareOriginalOffsetsAgainstLegacyPreviewContentWithoutHeadings() {
        String source = "第1集\r\n甲\r\n第2集\r\n乙";
        var json = new ObjectMapper().valueToTree(workspace(source, ScriptEpisodeParser.parse(source)));
        assertThat(json.path("episodeWarnings")).isEmpty();
    }

    @Test
    void groupHeadingBeforeAnEpisodeIsNotMisreportedAsAShiftedBoundary() {
        String first = "第1集\n甲\n";
        String second = "第2集到第3集剧情\n一段分组概述\n第2集\n乙\n";
        String third = "第3集\n丙";
        var json = new ObjectMapper().valueToTree(workspace(first + second + third, List.of(
            new ScriptEpisodeResponse(1, "一", first),
            new ScriptEpisodeResponse(2, "二", second),
            new ScriptEpisodeResponse(3, "三", third))));
        assertThat(json.path("episodeWarnings")).isEmpty();
    }

    private ScriptWorkspaceResponse workspace(String source, List<ScriptEpisodeResponse> episodes) {
        return new ScriptWorkspaceResponse(1L,
            new ScriptResponse(1L, 1L, "测试", "UPLOAD", source, "DRAFT", 1L, null),
            List.of(), List.of(), List.of(), List.of(), List.of(), episodes, null, null);
    }
}
