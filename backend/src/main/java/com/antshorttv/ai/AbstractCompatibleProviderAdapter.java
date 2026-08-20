package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

abstract class AbstractCompatibleProviderAdapter extends AiProviderAdapter {
    protected final AiSecretCodec aiSecretCodec;

    AbstractCompatibleProviderAdapter(AiSecretCodec aiSecretCodec) {
        this.aiSecretCodec = aiSecretCodec;
    }

    @Override
    public AiTextResponse text(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiTextRequest request) {
        long started = System.currentTimeMillis();
        if (shouldUseLocalMock(config)) {
            String prompt = request.userPrompt() == null ? "" : request.userPrompt();
            return new AiTextResponse(prompt, "local-" + started, 0, 0, 0, elapsed(started), Map.of("mode", "local"));
        }
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        throw new AiGatewayException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED, provider.getName() + " 文本真实调用待接入。");
    }

    @Override
    public AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiImageRequest request) {
        long started = System.currentTimeMillis();
        if (shouldUseLocalMock(config)) {
            int count = request.count() == null ? 1 : request.count();
            return new AiImageResponse(List.of(), "local-" + started, elapsed(started), Map.of("count", count, "mode", "local"));
        }
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        throw new AiGatewayException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED, provider.getName() + " 图片真实调用待接入。");
    }

    protected boolean shouldUseLocalMock(AiProviderConfigEntity config) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKeyCipher() == null ? "" : aiSecretCodec.decrypt(config.getApiKeyCipher());
        return baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith("mock://") || baseUrl.contains("example.com")
            || "test-key".equals(apiKey) || apiKey.startsWith("sk-test-");
    }

    protected long elapsed(long started) {
        return Math.max(1, Duration.ofMillis(System.currentTimeMillis() - started).toMillis());
    }
}
