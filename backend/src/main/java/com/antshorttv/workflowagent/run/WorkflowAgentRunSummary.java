package com.antshorttv.workflowagent.run;

import java.time.LocalDateTime;

public record WorkflowAgentRunSummary(
    Long id,
    String agentCode,
    String runType,
    String status,
    Long projectId,
    Long episodeId,
    String finalOutput,
    String errorCode,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
}
