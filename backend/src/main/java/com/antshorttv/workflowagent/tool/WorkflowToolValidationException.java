package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowToolValidationException extends BusinessException {
    private final Map<String, Object> details;

    public WorkflowToolValidationException(String message, Map<String, ?> details) {
        super(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID, message);
        this.details = Map.copyOf(new LinkedHashMap<>(details));
    }

    public Map<String, Object> details() {
        return details;
    }
}
