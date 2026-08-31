package com.antshorttv.workflowagent.run;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WorkflowAgentRunDetail(
    Long id,
    Long agentId,
    String agentCode,
    String runType,
    Long tenantId,
    Long userId,
    Long projectId,
    Long episodeId,
    Long scriptId,
    Long taskId,
    Long analysisStageId,
    String status,
    Long modelId,
    BigDecimal temperature,
    Integer maxTokens,
    Integer maxSteps,
    String promptSnapshot,
    List<WorkflowAgentSkillSnapshot> skillSnapshots,
    List<String> toolCodes,
    String finalOutput,
    String errorCode,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    List<WorkflowAgentRunStepView> steps
) {
}
