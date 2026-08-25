package com.antshorttv.review;

import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;

record ReviewExecutionOutcome(AiInvocationResult<AiTextResponse> invocation) {
    static ReviewExecutionOutcome empty() {
        return new ReviewExecutionOutcome(null);
    }
}
