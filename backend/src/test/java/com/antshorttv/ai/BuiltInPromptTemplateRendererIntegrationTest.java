package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The production renderer needs classpath resources only, even with no definition database. */
class BuiltInPromptTemplateRendererIntegrationTest {
    @Test
    void allCurrentTextWorkflowsRenderWithoutARegistryOrDatabase() {
        var renderer = new BuiltInPromptTemplateRenderer();
        assertThat(renderer.render("script-rewrite", Map.of("scriptContent", "正文", "rewriteRequirement", "精简"))).contains("正文", "精简");
        assertThat(renderer.render("video.understanding.analysis", Map.of("episodeNo", 3))).contains("# 第3集");
        assertThat(renderer.render("video.script.draft", Map.of("episodeNo", 3, "normalizedJson", "{}"))).contains("第 3 集");
    }
}
