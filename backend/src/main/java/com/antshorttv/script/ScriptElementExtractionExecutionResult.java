package com.antshorttv.script;

import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;
import java.util.List;

record ScriptElementExtractionExecutionResult(
    ScriptElementExtractionResult result,
    List<AiInvocationResult<AiTextResponse>> invocations
) {
}
