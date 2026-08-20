package com.antshorttv.ai;

public abstract class AiGateway {
    public abstract AiTextResponse text(AiContext context, AiTextRequest request);

    public abstract AiImageResponse image(AiContext context, AiImageRequest request);

    public Object video(AiContext context, Object request) {
        throw new AiGatewayException(com.antshorttv.common.ErrorCode.AI_PROVIDER_NOT_SUPPORTED, "视频模型 Gateway 暂未接入。");
    }

    public Object audio(AiContext context, Object request) {
        throw new AiGatewayException(com.antshorttv.common.ErrorCode.AI_PROVIDER_NOT_SUPPORTED, "音频模型 Gateway 暂未接入。");
    }
}
