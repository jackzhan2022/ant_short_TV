package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EpisodePromptContextFactoryTest {
    private final EpisodePromptContextFactory factory = new EpisodePromptContextFactory(new ObjectMapper());

    @Test
    void serializesOneStablePrefixForSummaryRecognitionAndStoryboardWithoutGeneratedSummary() {
        var summary = factory.build(7L, 8L, 9L, 10L, 11L, "fp-1",
            "{\"story\":\"global\"}", "第1集\n第一段\n\n第二段");
        var recognition = factory.build(7L, 8L, 9L, 10L, 11L, "fp-1",
            "{\"story\":\"global\"}", "第1集\n第一段\n\n第二段");

        assertThat(summary.commonPrefix()).isEqualTo(recognition.commonPrefix())
            .contains("公共剧集上下文", "P0001", "P0002", "第二段")
            .doesNotContain("分集概要", "summary", "runId", "timestamp", "assets");
        assertThat(summary.cacheKey()).isEqualTo(recognition.cacheKey());
        assertThat(summary.contextHash()).isEqualTo(recognition.contextHash());
    }

    @Test
    void isolatesTenantModelSourceAndFrozenGlobalUnderstandingAndUsesExplicitAbsentValue() {
        var base = factory.build(7L, 8L, 9L, 10L, 11L, "fp-1", null, "正文");
        var otherTenant = factory.build(70L, 8L, 9L, 10L, 11L, "fp-1", null, "正文");
        var otherModel = factory.build(7L, 8L, 9L, 10L, 12L, "fp-1", null, "正文");
        var otherSource = factory.build(7L, 8L, 9L, 10L, 11L, "fp-2", null, "新正文");
        var withGlobal = factory.build(7L, 8L, 9L, 10L, 11L, "fp-1", "{}", "正文");

        assertThat(base.commonPrefix()).contains("\"globalUnderstanding\":null");
        assertThat(base.cacheKey()).isNotEqualTo(otherTenant.cacheKey())
            .isNotEqualTo(otherModel.cacheKey())
            .isNotEqualTo(otherSource.cacheKey())
            .isNotEqualTo(withGlobal.cacheKey());
    }
}
