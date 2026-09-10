package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.antshorttv.common.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** OpenAI-compatible adapter dedicated to the XiongXiongAI gateway. */
@Component
public class XiongXiongAiAdapter extends AbstractCompatibleProviderAdapter {
    private static final Set<String> IMAGE_MODELS = Set.of("gpt-image-2", "gpt-image-2.5-sunburst", "gpt-image-2.5-flare");
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public XiongXiongAiAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerCode() {
        return "OpenAI";
    }

    @Override
    public AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model,
                                 AiImageRequest request, String idempotencyKey) {
        if (!IMAGE_MODELS.contains(model.getModelCode())) return super.image(provider, config, model, request, idempotencyKey);
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        long started = System.currentTimeMillis();
        try {
            JsonNode options = model.getConfigJson() == null || model.getConfigJson().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(model.getConfigJson());
            String quality = option(options, "quality", "auto", qualities(model.getModelCode()));
            String background = option(options, "background", "auto", Set.of("auto", "opaque", "transparent"));
            String outputFormat = option(options, "outputFormat", "png", Set.of("png", "jpeg", "webp"));
            String moderation = option(options, "moderation", "auto", Set.of("auto", "low"));
            Integer compression = options.has("outputCompression") ? options.path("outputCompression").asInt(-1) : null;
            if (compression != null && (compression < 0 || compression > 100 || "png".equals(outputFormat))) {
                throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "outputCompression 仅支持 JPEG/WebP 的 0-100 整数。");
            }
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", model.getModelCode());
            body.put("prompt", request.prompt() == null ? "" : request.prompt());
            body.put("n", request.count() == null ? 1 : request.count());
            body.put("size", imageSize(request));
            body.put("quality", quality);
            body.put("background", background);
            body.put("output_format", outputFormat);
            body.put("moderation", moderation);
            if (compression != null) body.put("output_compression", compression);
            boolean hasReferences = request.referenceImages() != null && !request.referenceImages().isEmpty();
            if (hasReferences) body.put("images", request.referenceImages());
            String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl().replaceAll("/+$", "");
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + (hasReferences ? "/images/edits" : "/images/generations")))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiSecretCodec.requireDecrypted(config.getApiKeyCipher()))
                .header("Idempotency-Key", idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiGatewayException(response.statusCode() == 429 ? ErrorCode.AI_RATE_LIMIT : ErrorCode.AI_PROVIDER_ERROR,
                    "熊熊爱图片服务返回 HTTP " + response.statusCode() + "：" + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<String> images = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                String b64 = item.path("b64_json").asText();
                String url = item.path("url").asText();
                if (!b64.isBlank()) images.add("data:image/" + outputFormat + ";base64," + b64);
                else if (!url.isBlank()) images.add(url);
            }
            if (images.isEmpty()) throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "熊熊爱图片服务未返回图片数据。");
            return new AiImageResponse(images, root.path("id").asText(null), Math.max(1, System.currentTimeMillis() - started), Map.of("provider", provider.getCode()));
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "熊熊爱图片调用失败：" + exception.getMessage());
        }
    }

    private Set<String> qualities(String modelCode) {
        return "gpt-image-2".equals(modelCode) ? Set.of("auto", "low", "medium", "high")
            : Set.of("auto", "low", "medium", "high", "xhigh", "max");
    }

    private String option(JsonNode options, String name, String fallback, Set<String> allowed) {
        String value = options.path(name).asText(fallback).trim().toLowerCase();
        if (!allowed.contains(value)) throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "不支持的 " + name + "：" + value);
        return value;
    }

    private String imageSize(AiImageRequest request) {
        if (request.size() != null && !request.size().isBlank()) return request.size();
        return switch (request.aspectRatio() == null ? "" : request.aspectRatio()) {
            case "1:1" -> "1024x1024";
            case "3:4" -> "1024x1536";
            case "4:3" -> "1536x1024";
            case "16:9" -> "1536x864";
            case "9:16" -> "864x1536";
            default -> "auto";
        };
    }
}
