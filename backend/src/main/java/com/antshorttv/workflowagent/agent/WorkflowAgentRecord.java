package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WorkflowAgentRecord(
    Long id,
    String code,
    String name,
    String description,
    String systemPrompt,
    Long modelId,
    BigDecimal temperature,
    Integer maxTokens,
    Integer maxSteps,
    String status,
    Long revision,
    Long createdBy,
    Long updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> skillCodes,
    List<String> toolCodes
) {
}
