package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class WorkflowToolExecutor {
    public abstract JsonNode execute(ToolExecutionContext context, JsonNode arguments);
}
