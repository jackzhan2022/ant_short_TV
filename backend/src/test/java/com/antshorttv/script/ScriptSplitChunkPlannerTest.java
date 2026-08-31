package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptSplitChunkPlannerTest {
    private final ScriptSplitChunkPlanner planner = new ScriptSplitChunkPlanner();

    @Test
    void plansOverlappingChunksFromNarrativeStructureWithoutRequiringEpisodeLabels() {
        String source = "序幕\n" + "旁白推进。".repeat(28)
            + "\n\n内景 客厅 夜\n" + "人物争执。".repeat(35)
            + "\n\n门突然打开\n" + "真相揭晓。".repeat(32);

        List<ScriptSplitChunkPlanner.ChunkPlan> chunks = planner.plan(source,
            new ScriptSplitChunkPlanner.ChunkSettings(100, 150, 180, 20));

        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> {
            assertThat(chunk.contextEnd() - chunk.contextStart()).isLessThanOrEqualTo(180);
            assertThat(chunk.coreStart()).isLessThan(chunk.coreEnd());
            assertThat(chunk.anchors()).allSatisfy(anchor -> {
                assertThat(anchor.marker().length()).isLessThanOrEqualTo(500);
                assertThat(source.startsWith(anchor.marker(), anchor.offset())).isTrue();
            });
        });
        assertThat(chunks.get(1).contextStart()).isLessThan(chunks.get(0).contextEnd());
        assertThat(chunks).extracting(ScriptSplitChunkPlanner.ChunkPlan::boundarySignal)
            .containsAnyOf("SCENE_HEADING", "PARAGRAPH", "LINE", "HARD_LIMIT");
    }

    @Test
    void neverCutsInsideAnEmojiSurrogatePair() {
        String source = "对白。".repeat(26) + "🎬" + "动作。".repeat(30);
        List<ScriptSplitChunkPlanner.ChunkPlan> chunks = planner.plan(source,
            new ScriptSplitChunkPlanner.ChunkSettings(75, 79, 85, 7));

        assertThat(chunks).allSatisfy(chunk -> {
            assertBoundary(source, chunk.coreStart());
            assertBoundary(source, chunk.coreEnd());
            assertBoundary(source, chunk.contextStart());
            assertBoundary(source, chunk.contextEnd());
        });
    }

    private void assertBoundary(String source, int boundary) {
        if (boundary > 0 && boundary < source.length()) {
            assertThat(Character.isHighSurrogate(source.charAt(boundary - 1))
                && Character.isLowSurrogate(source.charAt(boundary))).isFalse();
        }
    }
}
