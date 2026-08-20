package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class AbstractCompatibleProviderAdapter extends AiProviderAdapter {
    protected final AiSecretCodec aiSecretCodec;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    AbstractCompatibleProviderAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        this.aiSecretCodec = aiSecretCodec;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
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
        try {
            JsonNode root = postJson(config, chatCompletionsUri(config), chatPayload(model, request));
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                content = root.path("choices").path(0).path("text").asText("");
            }
            JsonNode usage = root.path("usage");
            return new AiTextResponse(
                content,
                root.path("id").asText(null),
                nullableInt(usage, "prompt_tokens"),
                nullableInt(usage, "completion_tokens"),
                nullableInt(usage, "total_tokens"),
                elapsed(started),
                Map.of("provider", provider.getCode())
            );
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "AI 文本调用失败：" + exception.getMessage());
        }
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
        try {
            JsonNode root = postJson(config, imagesUri(config), imagePayload(model, request));
            List<String> imageUrls = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                String url = item.path("url").asText(null);
                if (url != null && !url.isBlank()) {
                    imageUrls.add(url);
                }
            }
            return new AiImageResponse(
                imageUrls,
                root.path("id").asText("image-" + System.currentTimeMillis()),
                elapsed(started),
                Map.of("provider", provider.getCode())
            );
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "AI 图片调用失败：" + exception.getMessage());
        }
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

    private JsonNode postJson(AiProviderConfigEntity config, URI uri, Map<String, Object> payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + aiSecretCodec.decrypt(config.getApiKeyCipher()))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "AI 服务商返回 HTTP %d。".formatted(response.statusCode()));
        }
        return objectMapper.readTree(response.body());
    }

    private URI chatCompletionsUri(AiProviderConfigEntity config) {
        return endpoint(config, "/chat/completions");
    }

    private URI imagesUri(AiProviderConfigEntity config) {
        return endpoint(config, "/images/generations");
    }

    private URI endpoint(AiProviderConfigEntity config, String endpoint) {
        String baseUrl = config.getBaseUrl().endsWith("/")
            ? config.getBaseUrl().substring(0, config.getBaseUrl().length() - 1)
            : config.getBaseUrl();
        String configuredEndpoint = config.getExtraConfig() != null && config.getExtraConfig().startsWith("/")
            ? config.getExtraConfig()
            : endpoint;
        return URI.create(baseUrl + configuredEndpoint);
    }

    private Map<String, Object> chatPayload(AiModelEntity model, AiTextRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt() == null ? "" : request.userPrompt()));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", model.getModelCode());
        payload.put("messages", messages);
        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            payload.put("max_tokens", request.maxTokens());
        }
        return payload;
    }

    private Map<String, Object> imagePayload(AiModelEntity model, AiImageRequest request) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", model.getModelCode());
        payload.put("prompt", request.prompt() == null ? "" : request.prompt());
        payload.put("n", request.count() == null ? 1 : request.count());
        payload.put("size", request.size() == null || request.size().isBlank() ? sizeFromAspectRatio(request.aspectRatio()) : request.size());
        if (request.negativePrompt() != null && !request.negativePrompt().isBlank()) {
            payload.put("negative_prompt", request.negativePrompt());
        }
        return payload;
    }

    private String sizeFromAspectRatio(String aspectRatio) {
        return switch (aspectRatio == null ? "" : aspectRatio) {
            case "1:1" -> "1024x1024";
            case "3:4" -> "768x1024";
            case "4:3" -> "1024x768";
            case "16:9" -> "1280x720";
            default -> "1024x1792";
        };
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }
}
