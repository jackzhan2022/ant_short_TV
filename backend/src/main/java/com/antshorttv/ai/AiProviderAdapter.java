package com.antshorttv.ai;

public abstract class AiProviderAdapter {
    public abstract String providerCode();

    public abstract AiTextResponse text(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiTextRequest request);

    public abstract AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiImageRequest request);

    public <T> AiProviderExecutionOutcome<T> submit(
        AiProviderEntity provider,
        AiProviderConfigEntity config,
        AiModelEntity model,
        AiProviderSubmissionRequest request
    ) {
        throw new UnsupportedOperationException("Provider does not support native asynchronous submission.");
    }

    public <T> AiProviderExecutionOutcome<T> poll(
        AiProviderEntity provider,
        AiProviderConfigEntity config,
        AiModelEntity model,
        AiProviderPollingRequest request
    ) {
        throw new UnsupportedOperationException("Provider does not support native asynchronous polling.");
    }
}
