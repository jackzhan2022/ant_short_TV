package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import org.junit.jupiter.api.Test;

class ScriptReviewAgentRunContractTest {
    @Test
    void markdownPhasesRequireOnlyTrustedReadsAndNoTerminalSave() {
        WorkflowAgentRunContract quick = WorkflowAgentRunContract.forReviewPhase("MARKDOWN_QUICK");
        assertThat(quick.requiredToolSequence()).containsExactly("read_review_context", "read_review_content");
        assertThat(quick.terminalToolCode()).isNull();
        WorkflowToolRunState state = new WorkflowToolRunState();
        quick.requireNext(state, "read_review_context");
        state.recordSuccess("read_review_context");
        quick.requireNext(state, "read_review_content");
        state.recordSuccess("read_review_content");
        quick.requireNext(state, "read_review_content");
        state.recordSuccess("read_review_content");
        quick.requireComplete(state);
        WorkflowAgentRunContract child = WorkflowAgentRunContract.forReviewPhase("MARKDOWN_DEEP_CHILD");
        assertThat(child.requiredToolSequence()).containsExactly("read_review_context", "read_review_content");
        assertThat(child.terminalToolCode()).isNull();
        WorkflowAgentRunContract aggregation = WorkflowAgentRunContract.forReviewPhase("MARKDOWN_DEEP_AGGREGATION");
        assertThat(aggregation.requiredToolSequence()).isEmpty();
        assertThat(aggregation.terminalToolCode()).isNull();
    }

    @Test
    void rejectsRetiredPhasesAndRequiresTrustedReadOrder() {
        for (String phase : java.util.List.of("QUICK", "DEEP_CHILD", "DEEP_SEMANTIC", "DEEP_AGGREGATION")) {
            assertThatThrownBy(() -> WorkflowAgentRunContract.forReviewPhase(phase))
                .isInstanceOf(BusinessException.class);
        }
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forReviewPhase("MARKDOWN_QUICK");
        WorkflowToolRunState state = new WorkflowToolRunState();
        assertThatThrownBy(() -> contract.requireNext(state, "read_review_content"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> contract.requireComplete(state)).isInstanceOf(BusinessException.class);
        state.recordSuccess("read_review_context");
        assertThatThrownBy(() -> contract.requireNext(state, "save_review_result"))
            .isInstanceOf(BusinessException.class);
    }
}
