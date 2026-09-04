package com.antshorttv.script;

import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;
import java.util.List;

record ScriptAiOperationExecutionResult(
    String resultType,
    Long resultId,
    List<AiInvocationResult<AiTextResponse>> invocations,
    List<com.antshorttv.workflowagent.run.WorkflowAgentModelCall> agentModelCalls
) {
    ScriptAiOperationExecutionResult(
        String resultType,
        Long resultId,
        List<AiInvocationResult<AiTextResponse>> invocations
    ) {
        this(resultType, resultId, invocations, List.of());
    }

    ScriptAiOperationExecutionResult {
        invocations = invocations == null ? List.of() : List.copyOf(invocations);
        agentModelCalls = agentModelCalls == null ? List.of() : List.copyOf(agentModelCalls);
    }

    AiInvocationResult<AiTextResponse> lastInvocation() {
        return invocations == null || invocations.isEmpty() ? null : invocations.get(invocations.size() - 1);
    }

    com.antshorttv.workflowagent.run.WorkflowAgentModelCall lastAgentModelCall() {
        return agentModelCalls.isEmpty() ? null : agentModelCalls.get(agentModelCalls.size() - 1);
    }
}
