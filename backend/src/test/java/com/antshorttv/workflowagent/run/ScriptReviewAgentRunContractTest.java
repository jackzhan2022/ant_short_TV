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
    void quickRequiresTrustedReadsThenExactlyOneFormalSave() {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forReviewPhase("QUICK");
        assertThat(contract.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_content", "save_review_result");
        assertReviewReadsBeforeSave(contract, "read_review_content");
    }

    @Test
    void childCanOnlyPersistItsCandidateAndAggregationMustReadCandidatesBeforeFormalSave() {
        WorkflowAgentRunContract child = WorkflowAgentRunContract.forReviewPhase("DEEP_CHILD");
        assertThat(child.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_content", "save_review_unit_result");
        assertReviewReadsBeforeSave(child, "read_review_content");
        WorkflowAgentRunContract aggregation = WorkflowAgentRunContract.forReviewPhase("DEEP_AGGREGATION");
        assertThat(aggregation.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_unit_results", "read_review_content", "save_review_result");
        assertReviewReadsBeforeSave(aggregation, "read_review_unit_results", "read_review_content");
    }

    @Test
    void semanticQualityRequiresFrozenCandidatesAndSourceBeforeOneTerminalSave() {
        WorkflowAgentRunContract quality = WorkflowAgentRunContract.forReviewPhase("DEEP_SEMANTIC");
        assertThat(quality.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_candidates", "read_review_content",
            "save_review_semantic_decisions");
        assertReviewReadsBeforeSave(quality, "read_review_content", "read_review_candidates");
    }

    private void assertReviewReadsBeforeSave(
        WorkflowAgentRunContract contract,
        String... reads
    ) {
        WorkflowToolRunState state = new WorkflowToolRunState();
        assertThatThrownBy(() -> contract.requireNext(state, contract.terminalToolCode()))
            .isInstanceOf(BusinessException.class);
        contract.requireNext(state, "read_review_context");
        state.recordSuccess("read_review_context");
        for (int index = 0; index < reads.length; index++) {
            String read = reads[index];
            contract.requireNext(state, read);
            state.recordSuccess(read);
            if (index == 0) {
                contract.requireNext(state, read);
                state.recordSuccess(read);
            }
            if (index < reads.length - 1) {
                assertThatThrownBy(() -> contract.requireNext(state, contract.terminalToolCode()))
                    .isInstanceOf(BusinessException.class);
            }
        }
        contract.requireNext(state, contract.terminalToolCode());
        state.recordSuccess(contract.terminalToolCode());
        contract.requireComplete(state);
        assertThatThrownBy(() -> contract.requireNext(state, contract.terminalToolCode()))
            .isInstanceOf(BusinessException.class);
    }
}
