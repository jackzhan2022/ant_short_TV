package com.antshorttv.review;

import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.util.List;

record ReviewExecutionOutcome(
    AiInvocationResult<AiTextResponse> invocation,
    List<WorkflowAgentModelCall> modelCalls
) {
    ReviewExecutionOutcome {
        modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    }

    ReviewExecutionOutcome(AiInvocationResult<AiTextResponse> invocation) {
        this(invocation, List.of());
    }

    static ReviewExecutionOutcome empty() {
        return new ReviewExecutionOutcome(null, List.of());
    }
}
