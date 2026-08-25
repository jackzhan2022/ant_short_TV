package com.antshorttv.script;

import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;
import java.util.List;

record ScriptAiOperationExecutionResult(
    String resultType,
    Long resultId,
    List<AiInvocationResult<AiTextResponse>> invocations
) {
    AiInvocationResult<AiTextResponse> lastInvocation() {
        return invocations == null || invocations.isEmpty() ? null : invocations.get(invocations.size() - 1);
    }
}
