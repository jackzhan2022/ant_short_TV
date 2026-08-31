package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RemainingAnalysisAgentRunContractTest {
    static java.util.stream.Stream<Arguments> contracts() {
        return java.util.stream.Stream.of(
            Arguments.of("short-drama-episode-splitting", "read_current_script", "save_episode_splitting"),
            Arguments.of("short-drama-episode-summary", "read_current_episode", "save_episode_summary"),
            Arguments.of("short-drama-asset-recognition", "read_current_episode", "save_episode_assets")
        );
    }

    @ParameterizedTest
    @MethodSource("contracts")
    void requiresTheAgentSpecificReadThenTerminalSave(String agentCode, String read, String save) {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forAgent(agentCode);
        assertThat(contract.requiredToolSequence()).containsExactly(read, save);
        assertThat(contract.terminalToolCode()).isEqualTo(save);

        WorkflowToolRunState state = new WorkflowToolRunState();
        assertThatThrownBy(() -> contract.requireNext(state, save))
            .isInstanceOf(BusinessException.class).hasMessageContaining("按顺序");
        state.recordSuccess(read);
        assertThatThrownBy(() -> contract.requireNext(state, read))
            .isInstanceOf(BusinessException.class).hasMessageContaining("按顺序");
        contract.requireNext(state, save);
        state.recordSuccess(save);
        contract.requireComplete(state);
        assertThat(contract.isTerminal(save)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("contracts")
    void rejectsCompletionWhenReadOrSaveIsMissing(String agentCode, String read, String save) {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forAgent(agentCode);
        WorkflowToolRunState empty = new WorkflowToolRunState();
        assertThatThrownBy(() -> contract.requireComplete(empty))
            .isInstanceOf(BusinessException.class).hasMessageContaining(read).hasMessageContaining(save);

        WorkflowToolRunState readOnly = new WorkflowToolRunState();
        readOnly.recordSuccess(read);
        assertThatThrownBy(() -> contract.requireComplete(readOnly))
            .isInstanceOf(BusinessException.class).hasMessageContaining(save);
    }

    @org.junit.jupiter.api.Test
    void splittingAcceptsTheFallbackSequenceButRejectsMixedTools() {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forAgent(
            "short-drama-episode-splitting");
        WorkflowToolRunState state = new WorkflowToolRunState();
        state.beginSplitFallback("OUTPUT_TRUNCATED");
        for (String tool : List.of(
            "read_script_structure", "analyze_script_chunks", "save_episode_splitting")) {
            contract.requireNext(state, tool);
            state.recordSuccess(tool);
        }
        contract.requireComplete(state);

        WorkflowToolRunState mixed = new WorkflowToolRunState();
        mixed.recordSuccess("read_current_script");
        assertThatThrownBy(() -> contract.requireNext(mixed, "read_script_structure"))
            .isInstanceOf(BusinessException.class);
    }
}
