package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import org.junit.jupiter.api.Test;

class PromptBackfillResponseTest {
    @Test
    void parsesTheModelJsonPayloadWithoutDerivingAnyPromptText() {
        var payload = ScriptWorkflowService.parsePromptBackfillResponse("""
            ```json
            {"characters":[{"id":7,"prompt":"角色 Markdown 提示词"}],"characterLooks":[{"id":8,"prompt":"性别:女；衣着描述:白裙"}]}
            ```
            """);

        assertThat(payload.path("characters").get(0).path("prompt").asText()).isEqualTo("角色 Markdown 提示词");
        assertThat(payload.path("characterLooks").get(0).path("prompt").asText()).isEqualTo("性别:女；衣着描述:白裙");
    }

    @Test
    void rejectsNonJsonModelOutput() {
        assertThatThrownBy(() -> ScriptWorkflowService.parsePromptBackfillResponse("生成提示词成功"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("JSON");
    }
}
