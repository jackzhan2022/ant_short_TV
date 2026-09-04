package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import org.junit.jupiter.api.Test;

class StoryboardAgentRunContractTest {
    @Test
    void requiresEveryTrustedReadInExactOrderBeforeOneTerminalSave() {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forAgent(
            StoryboardAgentBootstrap.AGENT_CODE);
        assertThat(contract.requiredToolSequence()).containsExactlyElementsOf(StoryboardAgentBootstrap.TOOLS);
        assertThat(contract.terminalToolCode()).isEqualTo("save_episode_storyboards");
        assertThat(contract.preparationToolCodes()).containsExactly(
            "read_current_episode", "read_adjacent_episodes", "read_script_analysis",
            "read_project_context", "read_script_assets");

        WorkflowToolRunState state = new WorkflowToolRunState();
        assertThatThrownBy(() -> contract.requireNext(state, "read_adjacent_episodes"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("按顺序");
        for (String tool : StoryboardAgentBootstrap.TOOLS) {
            contract.requireNext(state, tool);
            state.recordSuccess(tool);
        }
        contract.requireComplete(state);
        assertThatThrownBy(() -> contract.requireNext(state, "save_episode_storyboards"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMissingOrDuplicateRequiredCalls() {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forAgent(
            StoryboardAgentBootstrap.AGENT_CODE);
        WorkflowToolRunState state = new WorkflowToolRunState();
        state.recordSuccess("read_current_episode");
        assertThatThrownBy(() -> contract.requireNext(state, "read_current_episode"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> contract.requireComplete(state))
            .isInstanceOf(BusinessException.class).hasMessageContaining("save_episode_storyboards");
    }
}
