package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import org.junit.jupiter.api.Test;

class ScriptReviewAgentRunContractTest {
    @Test
    void quickRequiresTrustedReadsThenExactlyOneFormalSave() {
        WorkflowAgentRunContract contract = WorkflowAgentRunContract.forReviewPhase("QUICK");
        assertThat(contract.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_content", "read_review_issue_history", "save_review_result");
        assertReviewReadsBeforeSave(contract, "read_review_content", "read_review_issue_history");
    }

    @Test
    void childCanOnlyPersistItsCandidateAndAggregationMustReadCandidatesBeforeFormalSave() {
        WorkflowAgentRunContract child = WorkflowAgentRunContract.forReviewPhase("DEEP_CHILD");
        assertThat(child.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_content", "read_review_issue_history", "save_review_unit_result");
        assertReviewReadsBeforeSave(child, "read_review_issue_history", "read_review_content");
        WorkflowAgentRunContract aggregation = WorkflowAgentRunContract.forReviewPhase("DEEP_AGGREGATION");
        assertThat(aggregation.requiredToolSequence()).containsExactly(
            "read_review_context", "read_review_issue_history", "read_review_unit_results", "save_review_result");
        assertReviewReadsBeforeSave(aggregation, "read_review_unit_results", "read_review_issue_history");
    }

    private void assertReviewReadsBeforeSave(
        WorkflowAgentRunContract contract,
        String firstRead,
        String secondRead
    ) {
        WorkflowToolRunState state = new WorkflowToolRunState();
        assertThatThrownBy(() -> contract.requireNext(state, contract.terminalToolCode()))
            .isInstanceOf(BusinessException.class);
        contract.requireNext(state, "read_review_context");
        state.recordSuccess("read_review_context");
        contract.requireNext(state, firstRead);
        state.recordSuccess(firstRead);
        contract.requireNext(state, firstRead);
        state.recordSuccess(firstRead);
        assertThatThrownBy(() -> contract.requireNext(state, contract.terminalToolCode()))
            .isInstanceOf(BusinessException.class);
        contract.requireNext(state, secondRead);
        state.recordSuccess(secondRead);
        contract.requireNext(state, contract.terminalToolCode());
        state.recordSuccess(contract.terminalToolCode());
        contract.requireComplete(state);
        assertThatThrownBy(() -> contract.requireNext(state, contract.terminalToolCode()))
            .isInstanceOf(BusinessException.class);
    }
}
