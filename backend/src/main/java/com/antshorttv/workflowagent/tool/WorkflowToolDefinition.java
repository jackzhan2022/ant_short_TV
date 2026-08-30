package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record WorkflowToolDefinition(
    String code,
    String name,
    String description,
    JsonNode inputSchema,
    JsonNode outputSchema,
    ToolRiskLevel riskLevel,
    ToolFailurePolicy failurePolicy,
    WorkflowToolExecutor executor
) {
    public WorkflowToolDefinition {
        if (code == null || !code.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Tool code must be stable snake_case");
        }
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool name and description are required");
        }
        Objects.requireNonNull(inputSchema, "inputSchema");
        Objects.requireNonNull(outputSchema, "outputSchema");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        Objects.requireNonNull(executor, "executor");
    }

    public WorkflowToolMetadata metadata() {
        return new WorkflowToolMetadata(code, name, description, inputSchema.deepCopy(),
            outputSchema.deepCopy(), riskLevel, failurePolicy);
    }
}
