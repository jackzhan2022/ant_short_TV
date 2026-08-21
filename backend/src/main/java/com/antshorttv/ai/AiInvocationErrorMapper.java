package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class AiInvocationErrorMapper {
    public AiGatewayException normalize(Throwable exception, AiCapability capability) {
        if (exception instanceof AiGatewayException aiGatewayException) {
            return aiGatewayException;
        }
        if (exception instanceof BusinessException businessException) {
            return new AiGatewayException(businessException.getErrorCode(), businessException.getMessage());
        }
        if (exception instanceof HttpTimeoutException || exception instanceof TimeoutException) {
            return new AiGatewayException(ErrorCode.AI_PROVIDER_TIMEOUT, capabilityName(capability) + "调用超时。");
        }
        String message = exception == null || exception.getMessage() == null ? "未知错误" : exception.getMessage();
        return new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, capabilityName(capability) + "调用失败：" + message);
    }

    public String diagnosticCategory(ErrorCode errorCode) {
        if (errorCode == null) {
            return "UNKNOWN";
        }
        return switch (errorCode) {
            case AI_AUTH_FAILED -> "AUTH";
            case AI_QUOTA_EXCEEDED -> "QUOTA";
            case AI_RATE_LIMIT -> "RATE_LIMIT";
            case AI_PROVIDER_TIMEOUT -> "TIMEOUT";
            case AI_PROVIDER_NOT_SUPPORTED, AI_PROVIDER_NOT_FOUND, AI_PROVIDER_DISABLED, AI_MODEL_NOT_FOUND, AI_MODEL_DISABLED -> "UNAVAILABLE";
            case AI_RESPONSE_INVALID -> "INVALID_RESPONSE";
            case VALIDATION_ERROR -> "VALIDATION";
            default -> "PROVIDER_ERROR";
        };
    }

    private String capabilityName(AiCapability capability) {
        return capability == null ? "AI" : capability.name().toLowerCase(Locale.ROOT);
    }
}
