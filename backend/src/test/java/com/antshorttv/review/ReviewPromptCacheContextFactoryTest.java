package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewPromptCacheContextFactoryTest {
    private final ReviewPromptCacheContextFactory factory =
        new ReviewPromptCacheContextFactory(new ObjectMapper());

    @Test
    void createsDeterministicPrefixWithoutHistoryField() {
        var first = factory.build(2L, 9L, 3L, "skills-v1", frozen("正文"));
        var repeated = factory.build(2L, 9L, 3L, "skills-v1", frozen("正文"));

        assertThat(repeated).isEqualTo(first);
        assertThat(first.commonPrefix()).contains("公共审核上下文", "正文")
            .doesNotContain("\"history\"", "R-1");
        assertThat(first.cacheKey()).startsWith("script-review:");
    }

    @Test
    void changesIdentityWhenTenantModelRulesOrContentChangesButNotHistory() {
        var baseline = factory.build(2L, 9L, 3L, "skills-v1", frozen("正文"));

        assertThat(factory.build(3L, 9L, 3L, "skills-v1", frozen("正文")).cacheKey())
            .isNotEqualTo(baseline.cacheKey());
        assertThat(factory.build(2L, 10L, 3L, "skills-v1", frozen("正文")).cacheKey())
            .isNotEqualTo(baseline.cacheKey());
        assertThat(factory.build(2L, 9L, 4L, "skills-v1", frozen("正文")).cacheKey())
            .isNotEqualTo(baseline.cacheKey());
        assertThat(factory.build(2L, 9L, 3L, "skills-v2", frozen("正文")).cacheKey())
            .isNotEqualTo(baseline.cacheKey());
        assertThat(factory.build(2L, 9L, 3L, "skills-v1", frozen("新正文")).cacheKey())
            .isNotEqualTo(baseline.cacheKey());
        assertThat(factory.build(2L, 9L, 3L, "skills-v1", frozen("正文")).cacheKey())
            .isEqualTo(baseline.cacheKey());
    }

    private ReviewContentService.FrozenReview frozen(String content) {
        String hash = ReviewContentService.hash(content);
        return new ReviewContentService.FrozenReview(
            content, hash, "scope", "dimensions", "snapshot", List.of(
                new ReviewContentService.Segment(
                    "episode:1/scene:1/offset:0", 1, "1", 1, 1, 1, 1,
                    0, content.length(), content, hash
                )
            ), 1
        );
    }
}
