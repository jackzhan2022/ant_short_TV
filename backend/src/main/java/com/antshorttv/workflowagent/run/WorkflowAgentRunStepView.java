package com.antshorttv.workflowagent.run;

import java.time.LocalDateTime;

public record WorkflowAgentRunStepView(
    Integer stepNo,
    String stepType,
    String status,
    Long aiCallLogId,
    String toolCode,
    String inputJson,
    String outputJson,
    String errorCode,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {
}
