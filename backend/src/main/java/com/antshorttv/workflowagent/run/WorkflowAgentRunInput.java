package com.antshorttv.workflowagent.run;

public record WorkflowAgentRunInput(
    String agentCode,
    String input,
    Long tenantId,
    Long projectId,
    Long episodeId,
    Long taskId,
    Long userId
) {
}
