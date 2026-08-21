package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

public class AiGatewayException extends BusinessException {
    private final Long aiCallLogId;

    public AiGatewayException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AiGatewayException(ErrorCode errorCode, String message, Long aiCallLogId) {
        super(errorCode, message);
        this.aiCallLogId = aiCallLogId;
    }

    public Long getAiCallLogId() {
        return aiCallLogId;
    }
}
