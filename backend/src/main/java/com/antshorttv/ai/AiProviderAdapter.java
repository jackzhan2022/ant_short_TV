package com.antshorttv.ai;

public abstract class AiProviderAdapter {
    public abstract String providerCode();

    public abstract AiTextResponse text(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiTextRequest request);

    public abstract AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiImageRequest request);
}
