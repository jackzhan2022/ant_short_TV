package com.antshorttv.video;

import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiProviderConfigEntity;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QwenVideoUnderstandingAdapter {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(300);

    private final AiSecretCodec aiSecretCodec;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenVideoUnderstandingAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        this.aiSecretCodec = aiSecretCodec;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public VideoUnderstandingResponse videoUnderstanding(
        AiProviderEntity provider,
        AiProviderConfigEntity config,
        AiModelEntity model,
        VideoUnderstandingRequest request
    ) {
        long started = System.currentTimeMillis();
        validate(config, model, request);
        try {
            JsonNode root = postJson(config, endpoint(config), payload(model, request));
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "Qwen 视频理解响应缺少内容。");
            }
            JsonNode usage = root.path("usage");
            return new VideoUnderstandingResponse(
                content,
                root.path("id").asText(null),
                nullableInt(usage, "prompt_tokens"),
                nullableInt(usage, "completion_tokens"),
                nullableInt(usage, "total_tokens"),
                Math.max(1, System.currentTimeMillis() - started),
                Map.of("provider", provider.getCode())
            );
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_TIMEOUT, "Qwen 视频理解调用超时。");
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "Qwen 视频理解调用失败：" + exception.getMessage());
        }
    }

    private void validate(AiProviderConfigEntity config, AiModelEntity model, VideoUnderstandingRequest request) {
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 服务商未配置 Base URL。");
        }
        if (model.getModelCode() == null || model.getModelCode().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型未配置真实 Model Code。");
        }
        if (request.videoUrl() == null || request.videoUrl().isBlank()) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "视频 URL 不能为空。");
        }
    }

    private JsonNode postJson(AiProviderConfigEntity config, URI uri, Map<String, Object> payload) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + aiSecretCodec.requireDecrypted(config.getApiKeyCipher()))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiGatewayException(
                errorCodeForStatus(response.statusCode()),
                "Qwen 视频理解返回 HTTP %d，URL：%s，Content-Type：%s，响应：%s"
                    .formatted(response.statusCode(), uri, contentType, responseSummary(response.body()))
            );
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException exception) {
            throw new AiGatewayException(
                ErrorCode.AI_PROVIDER_ERROR,
                "Qwen 视频理解返回非 JSON 响应，URL：%s，Content-Type：%s，响应：%s"
                    .formatted(uri, contentType, responseSummary(response.body()))
            );
        }
    }

    private ErrorCode errorCodeForStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return ErrorCode.AI_AUTH_FAILED;
        }
        if (statusCode == 429) {
            return ErrorCode.AI_RATE_LIMIT;
        }
        if (statusCode == 408 || statusCode == 504) {
            return ErrorCode.AI_PROVIDER_TIMEOUT;
        }
        return ErrorCode.AI_PROVIDER_ERROR;
    }

    private URI endpoint(AiProviderConfigEntity config) {
        String baseUrl = config.getBaseUrl().endsWith("/")
            ? config.getBaseUrl().substring(0, config.getBaseUrl().length() - 1)
            : config.getBaseUrl();
        String configuredEndpoint = config.getExtraConfig() != null && config.getExtraConfig().startsWith("/")
            ? config.getExtraConfig()
            : "/chat/completions";
        return URI.create(baseUrl + configuredEndpoint);
    }

    private Map<String, Object> payload(AiModelEntity model, VideoUnderstandingRequest request) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", model.getModelCode());
        payload.put("messages", List.of(Map.of(
            "role",
            "user",
            "content",
            List.of(
                Map.of("type", "video_url", "video_url", Map.of("url", request.videoUrl())),
                Map.of("type", "text", "text", request.prompt() == null ? "" : request.prompt())
            )
        )));
        payload.put("temperature", 0.1);
        payload.put("response_format", Map.of("type", "json_object"));
        return payload;
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private String responseSummary(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 300 ? compact : compact.substring(0, 300) + "...";
    }
}
