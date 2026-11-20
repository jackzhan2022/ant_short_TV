package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.antshorttv.common.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GeminiAdapter extends AbstractCompatibleProviderAdapter {
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GeminiAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
        this.json = objectMapper;
    }

    @Override
    public String providerCode() {
        return "Gemini";
    }

    @Override
    public AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config,
        AiModelEntity model, AiImageRequest request) {
        return image(provider, config, model, request, UUID.randomUUID().toString());
    }

    @Override
    public AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config,
        AiModelEntity model, AiImageRequest request, String idempotencyKey) {
        if (shouldUseLocalMock(config)) {
            return super.image(provider, config, model, request, idempotencyKey);
        }
        if (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank()) {
            throw new AiGatewayException(ErrorCode.AI_AUTH_FAILED, "Gemini 未配置 API Key。");
        }
        long started = System.currentTimeMillis();
        String apiKey = aiSecretCodec.requireDecrypted(config.getApiKeyCipher());
        try {
            String base = config.getBaseUrl().replaceAll("/+$", "");
            if (!base.endsWith("/v1beta") && !base.endsWith("/v1")) base += "/v1beta";
            String modelCode = model.getModelCode().replaceFirst("^models/", "");
            String encoded = URLEncoder.encode(modelCode, StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create(base + "/models/" + encoded + ":generateContent");
            var httpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .header("Idempotency-Key", idempotencyKey == null || idempotencyKey.isBlank()
                    ? UUID.randomUUID().toString() : idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(imagePayload(request))))
                .build();
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ErrorCode code = switch (response.statusCode()) {
                    case 401, 403 -> ErrorCode.AI_AUTH_FAILED;
                    case 429 -> ErrorCode.AI_RATE_LIMIT;
                    default -> response.statusCode() >= 500 ? ErrorCode.AI_PROVIDER_ERROR : ErrorCode.AI_RESPONSE_INVALID;
                };
                throw new AiGatewayException(code, "Gemini 图片接口返回 HTTP " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            List<String> images = new ArrayList<>();
            for (JsonNode candidate : root.path("candidates")) {
                for (JsonNode part : candidate.path("content").path("parts")) {
                    JsonNode data = part.has("inlineData") ? part.path("inlineData") : part.path("inline_data");
                    String mime = data.path("mimeType").asText(data.path("mime_type").asText(""));
                    String content = data.path("data").asText("");
                    if (mime.startsWith("image/") && !content.isBlank()) {
                        images.add("data:" + mime + ";base64," + content);
                    }
                }
            }
            if (images.isEmpty()) {
                throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "Gemini 响应未包含图片。");
            }
            return new AiImageResponse(images, root.path("responseId").asText(null), elapsed(started),
                Map.of("provider", provider.getCode()));
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_TIMEOUT, "Gemini 图片调用超时。");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "Gemini 图片调用被中断。");
        } catch (Exception exception) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_ERROR, "Gemini 图片调用失败。");
        }
    }

    private Map<String, Object> imagePayload(AiImageRequest request) throws Exception {
        List<Map<String, Object>> parts = new ArrayList<>();
        String prompt = request.prompt() == null ? "" : request.prompt();
        if (request.negativePrompt() != null && !request.negativePrompt().isBlank()) {
            prompt += "\nAvoid: " + request.negativePrompt();
        }
        parts.add(Map.of("text", prompt));
        if (request.referenceImages() != null) {
            for (String reference : request.referenceImages()) {
                int comma = reference.indexOf(',');
                if (reference.startsWith("data:image/") && comma > 0 && reference.substring(0, comma).endsWith(";base64")) {
                    String mime = reference.substring(5, reference.indexOf(';'));
                    parts.add(Map.of("inlineData", Map.of("mimeType", mime, "data", reference.substring(comma + 1))));
                } else if (reference.startsWith("https://") || reference.startsWith("http://")) {
                    var download = HttpRequest.newBuilder(URI.create(reference)).timeout(Duration.ofSeconds(30)).GET().build();
                    var response = client.send(download, HttpResponse.BodyHandlers.ofInputStream());
                    try (var stream = response.body()) {
                        String mime = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
                        if (response.statusCode() != 200 || !mime.startsWith("image/")) {
                            throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "Gemini 参考图下载失败或格式不正确。");
                        }
                        int limit = 20 * 1024 * 1024;
                        byte[] bytes = stream.readNBytes(limit + 1);
                        if (bytes.length == 0 || bytes.length > limit) {
                            throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "Gemini 参考图为空或超过 20 MB。");
                        }
                        parts.add(Map.of("inlineData", Map.of("mimeType", mime, "data", Base64.getEncoder().encodeToString(bytes))));
                    }
                } else {
                    throw new AiGatewayException(ErrorCode.AI_RESPONSE_INVALID, "Gemini 参考图地址不受支持。");
                }
            }
        }
        Map<String, Object> imageConfig = new LinkedHashMap<>();
        if (request.aspectRatio() != null && !request.aspectRatio().isBlank()) imageConfig.put("aspectRatio", request.aspectRatio());
        if (List.of("1K", "2K", "4K").contains(request.size() == null ? "" : request.size())) imageConfig.put("imageSize", request.size());
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("responseModalities", List.of("TEXT", "IMAGE"));
        if (!imageConfig.isEmpty()) generation.put("imageConfig", imageConfig);
        if (request.count() != null && request.count() > 1) generation.put("candidateCount", request.count());
        return Map.of("contents", List.of(Map.of("role", "user", "parts", parts)), "generationConfig", generation);
    }
}
