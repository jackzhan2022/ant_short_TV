package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import org.junit.jupiter.api.Test;

class AiInvocationErrorMapperTest {

    private final AiInvocationErrorMapper mapper = new AiInvocationErrorMapper();

    @Test
    void keepsExistingAiGatewayErrorCodes() {
        AiGatewayException exception = new AiGatewayException(ErrorCode.AI_RATE_LIMIT, "too many requests");

        AiGatewayException mapped = mapper.normalize(exception, AiCapability.TEXT);

        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.AI_RATE_LIMIT);
        assertThat(mapped.getMessage()).contains("too many requests");
    }

    @Test
    void mapsUnexpectedErrorsToProviderError() {
        AiGatewayException mapped = mapper.normalize(new IllegalStateException("socket closed"), AiCapability.TEXT);

        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_ERROR);
        assertThat(mapped.getMessage()).contains("socket closed");
    }

    @Test
    void mapsBusinessValidationErrorsWithoutHidingCode() {
        BusinessException exception = new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "解析失败");

        AiGatewayException mapped = mapper.normalize(exception, AiCapability.VIDEO_UNDERSTANDING);

        assertThat(mapped.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID);
        assertThat(mapped.getMessage()).contains("解析失败");
    }
}
