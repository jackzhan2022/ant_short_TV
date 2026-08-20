package com.antshorttv.ai;

public record AiModelRoute(
    AiModelEntity model,
    AiProviderEntity provider,
    AiProviderConfigEntity providerConfig,
    AiProviderAdapter adapter
) {
}
