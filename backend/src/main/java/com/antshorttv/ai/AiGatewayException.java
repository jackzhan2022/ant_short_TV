package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

public class AiGatewayException extends BusinessException {
    public AiGatewayException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
