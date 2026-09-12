package com.antshorttv.review;

import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.util.List;

record ReviewExecutionOutcome(
    List<WorkflowAgentModelCall> modelCalls
) {
    ReviewExecutionOutcome {
        modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    }

    static ReviewExecutionOutcome empty() {
        return new ReviewExecutionOutcome(List.of());
    }
}
