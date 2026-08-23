package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltInAgentRegistryTest {

    private final BuiltInAgentRegistry registry = new BuiltInAgentRegistry();

    @Test
    void resolvesCharacterExtractionAgentAndComposesSkillsInOrder() {
        BuiltInAgentDefinition agent = registry.findByScene(AiBusinessScene.CHARACTER_EXTRACT);

        assertThat(agent.code()).isEqualTo("script-character-extract");
        assertThat(agent.skillCodes()).containsExactly(
            "strict-json-output",
            "no-invention",
            "stable-entity-naming"
        );

        String prompt = registry.render(agent.code(), Map.of(
            "scriptTitle", "真假千金",
            "scriptContent", "林晚在天台拿出录音笔。"
        ));

        assertThat(prompt.indexOf("只返回合法 JSON"))
            .isLessThan(prompt.indexOf("不要编造"));
        assertThat(prompt.indexOf("不要编造"))
            .isLessThan(prompt.indexOf("角色名称要稳定"));
        assertThat(prompt).contains("真假千金", "林晚在天台拿出录音笔。");
    }

    @Test
    void rejectsMissingAgentVariablesBeforeProviderInvocation() {
        assertThatThrownBy(() -> registry.render(
                "script-character-extract",
                Map.of("scriptTitle", "真假千金")
            ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void exposesAllConfiguredBusinessAgents() {
        assertThat(registry.listAgents())
            .extracting(BuiltInAgentDefinition::scene)
            .contains(
                AiBusinessScene.SCRIPT_REWRITE,
                AiBusinessScene.CHARACTER_EXTRACT,
                AiBusinessScene.SCENE_EXTRACT,
                AiBusinessScene.PROP_EXTRACT,
                AiBusinessScene.VIDEO_UNDERSTANDING,
                AiBusinessScene.VIDEO_SCRIPT_DRAFT,
                AiBusinessScene.SCRIPT_REVIEW
            );
    }
}
