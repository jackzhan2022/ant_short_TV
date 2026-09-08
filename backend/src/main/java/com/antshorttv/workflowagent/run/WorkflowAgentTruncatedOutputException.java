package com.antshorttv.workflowagent.run;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;

public class WorkflowAgentTruncatedOutputException extends BusinessException {
    private final Long runId;
    private final String partialContent;
    private final List<WorkflowAgentModelCall> modelCalls;

    public WorkflowAgentTruncatedOutputException(Long runId, String partialContent,
        List<WorkflowAgentModelCall> modelCalls) {
        super(ErrorCode.VALIDATION_ERROR, "模型输出因长度限制被截断，请重试或调整审核范围。");
        this.runId = runId;
        this.partialContent = partialContent;
        this.modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
    }

    public Long runId() { return runId; }
    public String partialContent() { return partialContent; }
    public List<WorkflowAgentModelCall> modelCalls() { return modelCalls; }
}
