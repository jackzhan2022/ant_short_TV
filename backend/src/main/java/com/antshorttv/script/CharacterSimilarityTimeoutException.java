package com.antshorttv.script;

import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.common.ErrorCode;

/** A timed-out synchronous request may still be running upstream; do not resubmit it automatically. */
final class CharacterSimilarityTimeoutException extends AiGatewayException {
    CharacterSimilarityTimeoutException(AiGatewayException failure, AiInvocationRequest request) {
        super(ErrorCode.AI_PROVIDER_TIMEOUT,
            "角色形态检查等待模型响应超时，已停止自动重试；上游可能仍在处理，请核对结果与用量后再重试。",
            failure.getAiCallLogId());
        withCorrelation(request);
    }
}
