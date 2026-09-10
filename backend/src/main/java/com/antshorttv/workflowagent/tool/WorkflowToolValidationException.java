package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class WorkflowToolValidationException extends BusinessException {
    private final Map<String, Object> details;

    public WorkflowToolValidationException(String message, Map<String, ?> details) {
        super(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID, message);
        this.details = Map.copyOf(new LinkedHashMap<>(details));
    }

    public Map<String, Object> details() {
        return details;
    }

    public static WorkflowToolValidationException aggregate(List<String> errors) {
        String message = "资产保存校验失败（" + errors.size() + " 项）：\n" + String.join("\n", errors);
        // Run and step audit columns are varchar(2000); full feedback travels in tool-result details.
        if (message.length() > 1800) {
            int end = 1750;
            if (Character.isHighSurrogate(message.charAt(end - 1))) end--;
            message = message.substring(0, end) + "\n其余问题见 validationErrors。";
        }
        return new WorkflowToolValidationException(message, Map.of("validationErrors", List.copyOf(errors)));
    }
}
