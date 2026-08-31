package com.antshorttv.workflowagent.run;

import java.util.List;

public record WorkflowAgentRunResult(
    Long runId,
    String output,
    List<WorkflowAgentModelCall> modelCalls
) {
    public WorkflowAgentRunResult {
        modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    }

    public WorkflowAgentRunResult(Long runId, String output) {
        this(runId, output, List.of());
    }
}
