package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewUnitPlannerTest {
    private final ReviewContentService content = new ReviewContentService();
    private final ReviewUnitPlanner planner = new ReviewUnitPlanner();

    @Test
    void plansHeadingFreeUnicodeTextWithStableBoundedOverlappingOffsets() {
        String script = "林夏走进房间。\n\n顾言抬头。\n\n" + "对白很长。".repeat(20);
        var frozen = content.freeze(script, "ALL", Map.of(), List.of("台词合理性"));
        var first = planner.plan(script, "ALL", Map.of(), frozen, 35, 6);
        var second = planner.plan(script, "ALL", Map.of(), frozen, 35, 6);
        assertThat(first).isEqualTo(second).hasSizeGreaterThan(1);
        assertThat(first).allSatisfy(unit -> {
            assertThat(unit.endOffset() - unit.startOffset()).isLessThanOrEqualTo(35);
            assertThat(script.substring(unit.startOffset(), unit.endOffset())).isEqualTo(unit.content());
            assertThat(unit.fingerprint()).isEqualTo(ReviewContentService.hash(unit.content()));
        });
        assertThat(first.get(1).startOffset()).isLessThan(first.get(0).endOffset());
    }

    @Test
    void onlyPlansSelectedEpisodeOrSceneRanges() {
        String script = "第1集\n1-1 客厅 日 内\n第一集\n第2集\n2-1 公司 日 内\n第二集";
        var episode = content.freeze(script, "EPISODES", Map.of("episodeNos", List.of(2)), List.of("台词合理性"));
        assertThat(planner.plan(script, "EPISODES", Map.of("episodeNos", List.of(2)), episode, 100, 0))
            .extracting(ReviewUnitPlanner.Unit::content).allSatisfy(value -> assertThat(value).contains("第2集").doesNotContain("第一集"));
        var scene = content.freeze(script, "SCENES", Map.of("sceneKeys", List.of("2-1")), List.of("台词合理性"));
        assertThat(planner.plan(script, "SCENES", Map.of("sceneKeys", List.of("2-1")), scene, 100, 0))
            .extracting(ReviewUnitPlanner.Unit::content).containsExactly("2-1 公司 日 内\n第二集");
    }
}
