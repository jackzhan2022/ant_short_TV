package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScriptSplitChunkPlannerHeadingTest {
    @Test
    void exposesIndividualAndGroupHeadingsAsDifferentTrustedSignals() {
        String source = "第1集到第8集剧情\n第1集\nA\n第2集\nB";

        var anchors = new ScriptSplitChunkPlanner().plan(source,
            new ScriptSplitChunkPlanner.ChunkSettings(10, 100, 120, 0))
            .stream().flatMap(chunk -> chunk.anchors().stream()).toList();

        assertThat(anchors).extracting(ScriptSplitChunkPlanner.TrustedAnchor::signal)
            .containsExactly("EPISODE_GROUP_HEADING", "EPISODE_HEADING", "EPISODE_HEADING");
        assertThat(anchors).allSatisfy(anchor ->
            assertThat(source.startsWith(anchor.marker(), anchor.offset())).isTrue());
    }
}
