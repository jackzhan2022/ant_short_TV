package com.antshorttv.review;

import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiTextResponse;

final class ReviewInvocationException extends RuntimeException {
    private final AiInvocationResult<AiTextResponse> invocation;

    ReviewInvocationException(RuntimeException cause, AiInvocationResult<AiTextResponse> invocation) {
        super(cause.getMessage(), cause);
        this.invocation = invocation;
    }

    AiInvocationResult<AiTextResponse> invocation() {
        return invocation;
    }
}
