package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.common.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateRendererTest {
    private final PromptTemplateRenderer renderer = new BuiltInPromptTemplateRenderer();

    @Test
    void retiredAndUnknownTemplatesAreRejected() {
        for (String id : java.util.List.of("script.element.character.extract", "script.element.scene.extract",
            "script.element.prop.extract", "script-review", "script-episode-split", "unknown")) {
            assertThatThrownBy(() -> renderer.render(id, Map.of())).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void requiresAllVariablesAndPreservesLiteralUserContent() {
        assertThatThrownBy(() -> renderer.render("script-rewrite", Map.of("scriptContent", "正文")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("rewriteRequirement");
        assertThat(renderer.render("script-rewrite",
            Map.of("scriptContent", "${rewriteRequirement} $1 \\ ending", "rewriteRequirement", "保持人物")))
            .contains("${rewriteRequirement} $1 \\ ending", "保持人物", "直接输出改写后的中文短剧剧本");
    }

    @Test
    void videoTemplatesKeepTheirDifferentOutputContracts() {
        String analysis = renderer.render("video.understanding.analysis", Map.of("episodeNo", 3));
        assertThat(analysis).contains("# 第3集：标题", "## 3-1", "出场人物：", "——本集完",
            "只返回合法 JSON", "Markdown 标题和正文必须保留", "（OS）", "（VO）");
        String draft = renderer.render("video.script.draft", Map.of("episodeNo", 3, "normalizedJson", "{\"script\":\"正文\"}"));
        assertThat(draft).contains("第 3 集视频拆解 JSON", "按场次输出", "直接输出剧本正文")
            .doesNotContain("只返回合法 JSON");
    }
}
