package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowAgentPayloadGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "save_episode_splitting", "save_episode_summary", "save_episode_assets",
        "save_review_unit_result", "save_review_result"
    })
    void boundsEveryNewFormalSavePayload(String toolCode) {
        WorkflowAgentPayloadGuard.requireBounded(toolCode, "{}", 8);
        assertThatThrownBy(() -> WorkflowAgentPayloadGuard.requireBounded(
            toolCode, "{\"value\":\"too-large\"}", 8))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("负载");
    }
}
