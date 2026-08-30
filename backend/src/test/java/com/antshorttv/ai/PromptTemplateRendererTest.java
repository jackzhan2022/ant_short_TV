package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new BuiltInPromptTemplateRenderer();

    @Test
    void rendersElementExtractionTemplateWithStrictJsonInstructions() {
        String prompt = renderer.render(
            "script.element.character.extract",
            Map.of("scriptTitle", "真假千金", "scriptContent", "林晚在天台拿出录音笔。")
        );

        assertThat(prompt).contains("提取角色信息");
        assertThat(prompt).contains("只返回合法 JSON");
        assertThat(prompt).contains("\"characters\"");
        assertThat(prompt).contains("真假千金");
        assertThat(prompt).contains("林晚在天台拿出录音笔。");
    }

    @Test
    void rejectsMissingRequiredVariablesBeforeRendering() {
        assertThatThrownBy(() -> renderer.render(
                "script.element.scene.extract",
                Map.of("scriptTitle", "真假千金")
            ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void rendersVideoPromptsWithCurrentIntent() {
        String analysisPrompt = renderer.render(
            "video.understanding.analysis",
            Map.of("episodeNo", 3)
        );
        String draftPrompt = renderer.render(
            "video.script.draft",
            Map.of("episodeNo", 3, "normalizedJson", "{\"characters\":[]}")
        );

        assertThat(analysisPrompt)
            .contains("# 第3集：", "## 3-1", "出场人物：", "——本集完", "\"script\"", "只返回合法 JSON")
            .doesNotContain("\"characters\"", "\"timeline\"");
        assertThat(draftPrompt).contains("第 3 集视频拆解 JSON", "按场次输出", "{\"characters\":[]}");
    }
}
