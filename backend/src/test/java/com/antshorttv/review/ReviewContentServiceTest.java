package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewContentServiceTest {
    private final ReviewContentService service = new ReviewContentService();
    private final String script = """
        第1集
        1-1 客厅 日 内
        林夏：钥匙呢？
        1-2 门外 夜 外
        顾言握着钥匙。
        第2集
        2-1 仓库 夜 内
        林夏发现门已打开。
        """;

    @Test
    void filtersAllEpisodesAndScenesWithoutLeakingOtherContent() {
        assertThat(service.freeze(script, "ALL", Map.of(), List.of("台词合理性")).content())
            .contains("1-1 客厅", "2-1 仓库");
        assertThat(service.freeze(script, "EPISODES", Map.of("episodeNos", List.of(2)), List.of("台词合理性")).content())
            .contains("2-1 仓库").doesNotContain("1-1 客厅", "钥匙呢");
        assertThat(service.freeze(script, "SCENES", Map.of("sceneKeys", List.of("1-2")), List.of("台词合理性")).content())
            .contains("1-2 门外", "顾言握着钥匙").doesNotContain("1-1 客厅", "2-1 仓库");
    }

    @Test
    void producesStableCanonicalHashesAnchorsAndUnicodeOffsets() {
        ReviewContentService.FrozenReview first = service.freeze(script, "SCENES",
            Map.of("sceneKeys", List.of("2-1", "1-2")), List.of("道具连续性", "台词合理性"));
        ReviewContentService.FrozenReview second = service.freeze(script, "SCENES",
            Map.of("sceneKeys", List.of("1-2", "2-1")), List.of("台词合理性", "道具连续性"));
        assertThat(first.scopeHash()).isEqualTo(second.scopeHash());
        assertThat(first.dimensionsHash()).isEqualTo(second.dimensionsHash());
        assertThat(first.versionHash()).hasSize(64);
        assertThat(first.segments()).allSatisfy(segment -> {
            assertThat(segment.anchor()).isNotBlank();
            assertThat(script.substring(segment.startOffset(), segment.endOffset()))
                .contains(segment.content().lines().findFirst().orElseThrow());
        });
    }

    @Test
    void rejectsUnknownOrEmptySelectedScopeAndOversizedQuickReview() {
        assertThatThrownBy(() -> service.freeze(script, "SCENES", Map.of("sceneKeys", List.of("9-9")), List.of("台词合理性")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("场");
        ReviewContentService.FrozenReview frozen = service.freeze(script, "ALL", Map.of(), List.of("台词合理性"));
        assertThatThrownBy(() -> service.requireQuickBudget(frozen, 10))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REVIEW_SCOPE_TOO_LARGE_FOR_QUICK").hasMessageContaining("DEEP");
    }
}
