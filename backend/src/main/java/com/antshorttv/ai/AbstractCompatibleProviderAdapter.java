package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

abstract class AbstractCompatibleProviderAdapter extends AiProviderAdapter {
    protected final AiSecretCodec aiSecretCodec;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    @Value("${ai.testing.mock-provider-enabled:false}")
    private boolean mockProviderEnabled;

    AbstractCompatibleProviderAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        this.aiSecretCodec = aiSecretCodec;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public AiTextResponse text(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiTextRequest request) {
        return text(provider, config, model, request, UUID.randomUUID().toString());
    }

    @Override
    public AiTextResponse text(
        AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model,
        AiTextRequest request, String idempotencyKey
    ) {
        long started = System.currentTimeMillis();
        if (shouldUseLocalMock(config)) {
            String prompt = request.userPrompt() == null ? "" : request.userPrompt();
            return new AiTextResponse(localMockText(prompt), "local-" + started, 0, 0, 0, elapsed(started), Map.of("mode", "local"));
        }
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        String apiKey = apiKey(config);
        String effectiveIdempotencyKey = requireIdempotencyKey(idempotencyKey);
        try {
            JsonNode root = postJsonWithRetry(config, chatCompletionsUri(config), chatPayload(model, request),
                apiKey, request.timeoutSeconds(), request.retryCount(), effectiveIdempotencyKey);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                content = root.path("choices").path(0).path("text").asText("");
            }
            JsonNode usage = root.path("usage");
            String finishReason = root.path("choices").path(0).path("finish_reason").asText(null);
            List<AiToolCall> toolCalls = parseToolCalls(root.path("choices").path(0).path("message"));
            if (!toolCalls.isEmpty() && content != null && content.isBlank()) {
                content = null;
            }
            return new AiTextResponse(
                content,
                root.path("id").asText(null),
                nullableInt(usage, "prompt_tokens"),
                nullableInt(usage, "completion_tokens"),
                nullableInt(usage, "total_tokens"),
                elapsed(started),
                Map.of("provider", provider.getCode()),
                finishReason,
                "length".equalsIgnoreCase(finishReason),
                toolCalls
            );
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "AI 文本调用失败：" + exception.getMessage());
        }
    }

    @Override
    public AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiImageRequest request) {
        return image(provider, config, model, request, UUID.randomUUID().toString());
    }

    @Override
    public AiImageResponse image(
        AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model,
        AiImageRequest request, String idempotencyKey
    ) {
        long started = System.currentTimeMillis();
        if (shouldUseLocalMock(config)) {
            int count = request.count() == null ? 1 : request.count();
            return new AiImageResponse(List.of(), "local-" + started, elapsed(started), Map.of("count", count, "mode", "local"));
        }
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        String apiKey = apiKey(config);
        String effectiveIdempotencyKey = requireIdempotencyKey(idempotencyKey);
        try {
            JsonNode root = postJson(
                config, imagesUri(config), imagePayload(model, request), apiKey, REQUEST_TIMEOUT, effectiveIdempotencyKey);
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
        if (!mockProviderEnabled) {
            return false;
        }
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith("mock://") || baseUrl.contains("example.com")) {
            return true;
        }
        String apiKey = aiSecretCodec.requireDecrypted(config.getApiKeyCipher());
        return "test-key".equals(apiKey) || apiKey.startsWith("sk-test-");
    }

    protected long elapsed(long started) {
        return Math.max(1, Duration.ofMillis(System.currentTimeMillis() - started).toMillis());
    }

    private String localMockText(String prompt) {
        if (prompt.contains("剧情全局理解") || prompt.contains("全局理解") || prompt.contains("中文短剧结构分析助手")) {
            return "{\"logline\":\"\",\"themes\":[],\"characters\":[],\"relationships\":[],\"coreConflict\":\"\",\"turningPoints\":[],\"endingHook\":\"\"}";
        }
        if (prompt.contains("分集助手") || prompt.contains("剧集概要提炼") || prompt.contains("概要提炼")) {
            return "{\"episodes\":[{\"episodeNo\":1,\"title\":\"\",\"content\":\"\",\"summary\":\"\",\"highlights\":[],\"endingHook\":\"\"}]}";
        }
        if (prompt.contains("提取角色信息")) {
            if (prompt.contains("林晚")) {
                return "{\"characters\":[{\"name\":\"林晚\",\"roleType\":\"LEAD\",\"gender\":\"\",\"ageRange\":\"\",\"identity\":\"\",\"personality\":[],\"appearance\":\"\",\"prompt\":\"\"}]}";
            }
            return "{\"characters\":[{\"name\":\"主角\",\"roleType\":\"LEAD\",\"gender\":\"\",\"ageRange\":\"\",\"identity\":\"\",\"personality\":[],\"appearance\":\"\",\"prompt\":\"主角角色定妆照\"},{\"name\":\"反派\",\"roleType\":\"SUPPORTING\",\"gender\":\"\",\"ageRange\":\"\",\"identity\":\"\",\"personality\":[],\"appearance\":\"\",\"prompt\":\"反派角色定妆照\"}]}";
        }
        if (prompt.contains("提取场景信息")) {
            String firstScene = prompt.contains("林家老宅门口") ? "林家老宅门口" : "主场景";
            return "{\"scenes\":[{\"name\":\"" + firstScene + "\",\"sceneType\":\"EXTERIOR\",\"atmosphere\":\"紧张\",\"description\":\"故事主要发生地\",\"visualStyle\":\"电影感\",\"prompt\":\"" + firstScene + "场景\"},{\"name\":\"室内场景\",\"sceneType\":\"INTERIOR\",\"atmosphere\":\"压迫\",\"description\":\"室内戏场景\",\"visualStyle\":\"电影感\",\"prompt\":\"室内场景\"}]}";
        }
        if (prompt.contains("提取道具信息") || prompt.contains("提取关键道具信息")) {
            String propName = prompt.contains("录音笔") ? "录音笔" : "股权协议";
            return "{\"props\":[{\"name\":\"" + propName + "\",\"propType\":\"KEY_PROP\",\"appearance\":\"重要道具\",\"plotFunction\":\"推动剧情\",\"prompt\":\"" + propName + "道具特写\"}]}";
        }
        if (prompt.contains("资产识别助手") || prompt.contains("角色场景识别")) {
            return "{\"characters\":[\"主角\"],\"scenes\":[\"主场景\"],\"props\":[\"关键道具\"]}";
        }
        return prompt;
    }

    private String apiKey(AiProviderConfigEntity config) {
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        return aiSecretCodec.requireDecrypted(config.getApiKeyCipher());
    }

    private JsonNode postJsonWithRetry(
        AiProviderConfigEntity config,
        URI uri,
        Map<String, Object> payload,
        String apiKey,
        Integer timeoutSeconds,
        Integer retryCount,
        String idempotencyKey
    ) throws Exception {
        int attempts = 1 + Math.max(0, retryCount == null ? 0 : Math.min(5, retryCount));
        Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds == null ? 60 : timeoutSeconds));
        AiGatewayException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return postJson(config, uri, payload, apiKey, timeout, idempotencyKey);
            } catch (AiGatewayException exception) {
                last = exception;
                if ((exception.getErrorCode() != ErrorCode.AI_PROVIDER_ERROR
                    && exception.getErrorCode() != ErrorCode.AI_RATE_LIMIT) || attempt == attempts) throw exception;
            } catch (java.io.IOException exception) {
                if (attempt == attempts) throw exception;
            }
        }
        throw last;
    }

    private JsonNode postJson(
        AiProviderConfigEntity config, URI uri, Map<String, Object> payload, String apiKey, Duration timeout,
        String idempotencyKey
    ) throws Exception {
        String requestBody = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .header("Idempotency-Key", requireIdempotencyKey(idempotencyKey))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            ErrorCode errorCode = switch (response.statusCode()) {
                case 401, 403 -> ErrorCode.AI_AUTH_FAILED;
                case 429 -> ErrorCode.AI_RATE_LIMIT;
                default -> response.statusCode() >= 500
                    ? ErrorCode.AI_PROVIDER_ERROR : ErrorCode.AI_RESPONSE_INVALID;
            };
            throw new AiGatewayException(
                errorCode,
                "AI 服务商返回 HTTP %d，URL：%s，Content-Type：%s，响应：%s"
                    .formatted(response.statusCode(), uri, contentType, responseSummary(response.body()))
            );
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException exception) {
            throw new AiGatewayException(
                ErrorCode.AI_PROVIDER_ERROR,
                "AI 服务商返回非 JSON 响应，URL：%s，Content-Type：%s，响应：%s"
                    .formatted(uri, contentType, responseSummary(response.body()))
            );
        }
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank()
            ? UUID.randomUUID().toString()
            : idempotencyKey.trim();
    }

    private String responseSummary(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "...";
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
        List<Map<String, Object>> messages = request.messages().isEmpty()
            ? legacyMessages(request)
            : request.messages().stream().map(this::messagePayload).toList();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", model.getModelCode());
        payload.put("messages", messages);
        if (request.temperature() != null) {
            payload.put("temperature", request.temperature());
        }
        if (request.maxTokens() != null) {
            payload.put("max_tokens", request.maxTokens());
        }
        String modelCode = model.getModelCode() == null
            ? "" : model.getModelCode().toLowerCase(Locale.ROOT);
        if (request.thinkingMode() != null && modelCode.startsWith("deepseek-")) {
            payload.put("thinking", Map.of("type", request.thinkingMode()));
        }
        configuredReasoningEffort(model).ifPresent(effort -> payload.put("reasoning_effort", effort));
        if (request.topP() != null) {
            payload.put("top_p", request.topP());
        }
        if (Boolean.TRUE.equals(request.jsonMode())) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream().map(tool -> Map.of(
                "type", "function",
                "function", Map.of(
                    "name", tool.code(),
                    "description", tool.description(),
                    "parameters", tool.inputSchema()
                )
            )).toList());
        }
        return payload;
    }

    private java.util.Optional<String> configuredReasoningEffort(AiModelEntity model) {
        if (model.getConfigJson() == null || model.getConfigJson().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            String effort = objectMapper.readTree(model.getConfigJson()).path("reasoningEffort").asText();
            return Set.of("low", "medium", "high").contains(effort)
                ? java.util.Optional.of(effort)
                : java.util.Optional.empty();
        } catch (JsonProcessingException ignored) {
            return java.util.Optional.empty();
        }
    }

    private List<Map<String, Object>> legacyMessages(AiTextRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt() == null ? "" : request.userPrompt()));
        return messages;
    }

    private Map<String, Object> messagePayload(AiChatMessage message) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("role", message.role().name().toLowerCase(java.util.Locale.ROOT));
        if (message.content() != null) {
            payload.put("content", message.content());
        }
        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
        }
        if (!message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream().map(call -> Map.of(
                "id", call.id(),
                "type", "function",
                "function", Map.of("name", call.code(), "arguments", call.argumentsJson())
            )).toList());
        }
        return payload;
    }

    private List<AiToolCall> parseToolCalls(JsonNode message) {
        List<AiToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            String id = call.path("id").asText(null);
            String code = function.path("name").asText(null);
            if (id != null && code != null) {
                calls.add(new AiToolCall(id, code, function.path("arguments").asText("{}")));
            }
        }
        return List.copyOf(calls);
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
