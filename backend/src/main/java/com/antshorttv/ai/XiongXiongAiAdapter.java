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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** OpenAI-compatible adapter dedicated to the XiongXiongAI gateway. */
@Component
public class XiongXiongAiAdapter extends AbstractCompatibleProviderAdapter {
    private static final String DEFAULT_RESPONSES_MODEL = "gpt-5.6-terra";
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
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "AI 服务商未配置 API Key。");
        }
        long started = System.currentTimeMillis();
        try {
            JsonNode options = model.getConfigJson() == null || model.getConfigJson().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(model.getConfigJson());
            String quality = option(options, "quality", "auto", qualities(model.getModelCode()));
            String outputFormat = option(options, "outputFormat", "png", Set.of("png", "jpeg", "webp"));
            boolean hasReferences = request.referenceImages() != null && !request.referenceImages().isEmpty();

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "image_generation");
            tool.put("model", model.getModelCode());
            tool.put("size", imageSize(request));
            tool.put("quality", quality);
            tool.put("output_format", outputFormat);
            tool.put("action", hasReferences ? "edit" : "generate");

            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "input_text", "text", request.prompt() == null ? "" : request.prompt()));
            if (hasReferences) {
                for (String reference : request.referenceImages()) {
                    if (reference != null && !reference.isBlank()) {
                        content.add(Map.of("type", "input_image", "image_url", reference));
                    }
                }
            }
            Map<String, Object> input = Map.of("role", "user", "content", content);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", responsesModel(options));
            body.put("input", List.of(input));
            body.put("tools", List.of(tool));

            String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl().replaceAll("/+$", "");
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .timeout(Duration.ofMinutes(10))
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
            List<String> images = imageResults(root, outputFormat);
            if (images.isEmpty()) throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "熊熊爱图片服务未返回图片数据。");
            return new AiImageResponse(images, root.path("id").asText(null), Math.max(1, System.currentTimeMillis() - started),
                Map.of("provider", provider.getCode()));
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "熊熊爱图片调用失败：" + exception.getMessage());
        }
    }

    private List<String> imageResults(JsonNode root, String fallbackFormat) {
        List<String> images = new ArrayList<>();
        for (JsonNode item : root.path("output")) {
            if (!"image_generation_call".equals(item.path("type").asText())) continue;
            String result = item.path("result").asText();
            if (result.isBlank()) continue;
            String format = item.path("output_format").asText(fallbackFormat);
            images.add("data:image/" + format + ";base64," + result);
        }
        return images;
    }

    private String responsesModel(JsonNode options) {
        String configured = options.path("responsesModel").asText("").trim();
        return configured.isBlank() ? DEFAULT_RESPONSES_MODEL : configured;
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
