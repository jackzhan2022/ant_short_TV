package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedEpisodeBoundaryBuilderTest {
    private final TrustedEpisodeBoundaryBuilder builder = new TrustedEpisodeBoundaryBuilder();

    @Test
    void buildsCompleteBoundariesFromTrustedEpisodeHeadingsAndTheActualScriptTail() {
        String source = "前言\r\n第一集：开始\r\n正文一\r\n第二集：转折\r\n正文二\r\n第三集：结局\r\n最终画面。\r\n";
        List<ScriptSplitChunkPlanner.TrustedAnchor> anchors = List.of(
            anchor(source, "第三集：结局"),
            anchor(source, "第一集：开始"),
            anchor(source, "第二集：转折"),
            anchor(source, "第二集：转折"));

        List<EpisodeSplitBoundaryResolver.Boundary> boundaries = builder.build(source, anchors)
            .orElseThrow();

        assertThat(boundaries).hasSize(3);
        assertThat(boundaries.get(0).endMarker()).isEqualTo("第二集：转折");
        assertThat(boundaries.get(2).endMarker()).isEqualTo("最终画面。");
        assertThat(new EpisodeSplitBoundaryResolver().resolve(source, boundaries).stream()
            .map(ScriptEpisodeResponse::content).reduce("", String::concat)).isEqualTo(source);
    }

    @Test
    void declinesDeterministicConstructionWhenExplicitEpisodeHeadingsAreInsufficient() {
        assertThat(builder.build("无标题剧本", List.of())).isEmpty();
    }

    private ScriptSplitChunkPlanner.TrustedAnchor anchor(String source, String marker) {
        return new ScriptSplitChunkPlanner.TrustedAnchor(
            source.indexOf(marker), marker, "EPISODE_HEADING");
    }
}
