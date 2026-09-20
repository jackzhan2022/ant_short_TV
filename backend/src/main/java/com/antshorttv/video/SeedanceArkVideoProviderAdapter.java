package com.antshorttv.video;

import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiImageRequest;
import com.antshorttv.ai.AiImageResponse;
import com.antshorttv.ai.AiProviderAdapter;
import com.antshorttv.ai.AiProviderConfigEntity;
import com.antshorttv.ai.AiProviderExecutionOutcome;
import com.antshorttv.ai.AiProviderReconciliationStatus;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.ai.AiTextRequest;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SeedanceArkVideoProviderAdapter extends AiProviderAdapter {
    private static final String TASKS_PATH = "/contents/generations/tasks";

    private final AiSecretCodec secretCodec;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SeedanceArkVideoProviderAdapter(AiSecretCodec secretCodec, ObjectMapper objectMapper) {
        this.secretCodec = secretCodec;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String providerCode() {
        return "VOLCENGINE_ARK";
    }

    @Override
    public AiTextResponse text(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiTextRequest request) {
        throw new UnsupportedOperationException("Volcengine Ark Seedance only supports native video generation.");
    }

    @Override
    public AiImageResponse image(AiProviderEntity provider, AiProviderConfigEntity config, AiModelEntity model, AiImageRequest request) {
        throw new UnsupportedOperationException("Volcengine Ark Seedance only supports native video generation.");
    }

    public AiProviderExecutionOutcome<AiVideoProviderAdapter.VideoResult> submit(
        AiProviderConfigEntity providerConfig,
        AiModelEntity model,
        AiVideoTaskEntity task,
        String idempotencyKey
    ) throws Exception {
        String endpointId = requireEndpointId(model);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", endpointId);
        payload.put("content", content(task));
        payload.put("duration", task.durationSeconds == null ? 5 : task.durationSeconds);
        payload.put("ratio", task.aspectRatio == null || task.aspectRatio.isBlank() ? "9:16" : task.aspectRatio);
        payload.put("generate_audio", task.generateAudio == null || task.generateAudio);
        payload.put("watermark", Boolean.TRUE.equals(task.watermark));
        if (task.resolution != null && !task.resolution.isBlank()) {
            payload.put("resolution", task.resolution);
        }
        JsonNode body = post(providerConfig, TASKS_PATH, payload, idempotencyKey);
        String taskId = firstText(body, "id", "task_id", "taskId");
        if (taskId == null) {
            throw new IllegalStateException("火山方舟未返回任务 ID。");
        }
        return AiProviderExecutionOutcome.accepted(
            firstText(body, "request_id", "requestId"),
            taskId,
            Duration.ofSeconds(10),
            AiProviderReconciliationStatus.NOT_REQUIRED
        );
    }

    public AiProviderExecutionOutcome<AiVideoProviderAdapter.VideoResult> poll(
        AiProviderConfigEntity providerConfig,
        AiModelEntity model,
        String externalTaskId,
        String idempotencyKey
    ) throws Exception {
        requireEndpointId(model);
        if (externalTaskId == null || externalTaskId.isBlank()) {
            throw new IllegalArgumentException("火山方舟任务 ID 不能为空。");
        }
        JsonNode body = get(providerConfig, TASKS_PATH + "/" + externalTaskId, idempotencyKey);
        String status = normalizeStatus(firstText(body, "status", "task_status"));
        String requestId = firstText(body, "request_id", "requestId");
        if ("RUNNING".equals(status)) {
            return AiProviderExecutionOutcome.accepted(
                requestId, externalTaskId, Duration.ofSeconds(10), AiProviderReconciliationStatus.NOT_REQUIRED
            );
        }
        String videoUrl = firstText(body, "video_url", "videoUrl", "url");
        if (videoUrl == null) {
            videoUrl = firstText(body.path("content"), "video_url", "videoUrl", "url");
        }
        String error = firstText(body, "error_message", "message");
        if (error == null && body.path("error").isObject()) {
            error = firstText(body.path("error"), "message", "error_message");
        }
        if ("SUCCEEDED".equals(status) && videoUrl == null) {
            return AiProviderExecutionOutcome.completed(
                videoResult("FAILED", null, "火山方舟未返回视频地址。", body), requestId
            );
        }
        return AiProviderExecutionOutcome.completed(
            videoResult(status, videoUrl, error, body), requestId
        );
    }

    public void cancel(
        AiProviderConfigEntity providerConfig,
        AiModelEntity model,
        String externalTaskId,
        String idempotencyKey
    ) throws Exception {
        requireEndpointId(model);
        if (externalTaskId == null || externalTaskId.isBlank()) {
            throw new IllegalArgumentException("火山方舟任务 ID 不能为空。");
        }
        HttpRequest request = baseRequest(providerConfig, TASKS_PATH + "/" + externalTaskId, idempotencyKey)
            .DELETE()
            .build();
        HttpResponse<String> providerResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (providerResponse.statusCode() == 404) {
            return;
        }
        response(providerResponse);
    }

    private AiVideoProviderAdapter.VideoResult videoResult(
        String status,
        String videoUrl,
        String error,
        JsonNode body
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        copyLong(body, metadata, "created_at", "createdAt");
        copyLong(body, metadata, "updated_at", "updatedAt");
        copyLong(body, metadata, "execution_expires_after", "executionExpiresAfter");
        copyLong(body, metadata, "seed", "seed");
        copyText(body, metadata, "resolution", "resolution");
        copyText(body, metadata, "ratio", "ratio");
        copyDecimal(body, metadata, "duration", "duration");
        copyDecimal(body, metadata, "framespersecond", "fps");
        copyText(body, metadata, "service_tier", "serviceTier");
        copyBoolean(body, metadata, "generate_audio", "generateAudio");
        copyBoolean(body, metadata, "draft", "draft");
        copyLong(body, metadata, "priority", "priority");
        JsonNode usage = body.path("usage");
        copyLong(usage, metadata, "completion_tokens", "completionTokens");
        copyLong(usage, metadata, "total_tokens", "totalTokens");
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception exception) {
            throw new IllegalStateException("火山方舟结果元数据无法序列化。", exception);
        }
        return new AiVideoProviderAdapter.VideoResult(
            status,
            videoUrl,
            error,
            metadataJson,
            decimal(body, "duration"),
            firstText(body, "resolution"),
            firstText(body, "ratio"),
            longValue(body, "seed"),
            decimal(body, "framespersecond"),
            firstText(body, "service_tier"),
            booleanValue(body, "generate_audio"),
            longValue(usage, "completion_tokens"),
            longValue(usage, "total_tokens")
        );
    }

    private void copyText(JsonNode source, Map<String, Object> target, String sourceField, String targetField) {
        String value = firstText(source, sourceField);
        if (value != null) target.put(targetField, value);
    }

    private void copyLong(JsonNode source, Map<String, Object> target, String sourceField, String targetField) {
        Long value = longValue(source, sourceField);
        if (value != null) target.put(targetField, value);
    }

    private void copyDecimal(JsonNode source, Map<String, Object> target, String sourceField, String targetField) {
        BigDecimal value = decimal(source, sourceField);
        if (value != null) target.put(targetField, value);
    }

    private void copyBoolean(JsonNode source, Map<String, Object> target, String sourceField, String targetField) {
        Boolean value = booleanValue(source, sourceField);
        if (value != null) target.put(targetField, value);
    }

    private Long longValue(JsonNode source, String field) {
        JsonNode value = source.get(field);
        return value != null && value.isNumber() ? value.longValue() : null;
    }

    private BigDecimal decimal(JsonNode source, String field) {
        JsonNode value = source.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private Boolean booleanValue(JsonNode source, String field) {
        JsonNode value = source.get(field);
        return value != null && value.isBoolean() ? value.booleanValue() : null;
    }

    private List<Map<String, Object>> content(AiVideoTaskEntity task) {
        List<Map<String, Object>> content = new ArrayList<>();
        String prompt = task.compiledPrompt == null || task.compiledPrompt.isBlank()
            ? task.prompt : task.compiledPrompt;
        content.add(Map.of("type", "text", "text", prompt));
        if (task.requestSnapshotJson != null && !task.requestSnapshotJson.isBlank()) {
            try {
                JsonNode references = objectMapper.readTree(task.requestSnapshotJson).path("references");
                for (JsonNode item : references) {
                    JsonNode reference = item.path("reference");
                    String mediaType = reference.path("mediaType").asText().toLowerCase();
                    String field = mediaType + "_url";
                    Map<String, Object> media = new LinkedHashMap<>();
                    media.put("type", field);
                    media.put(field, Map.of("url", item.path("providerUrl").asText()));
                    media.put("role", reference.path("providerRole").asText());
                    content.add(media);
                }
                return content;
            } catch (Exception exception) {
                throw new IllegalStateException("Seedance 视频请求快照不正确。", exception);
            }
        }
        if (task.firstFrameUrl != null && !task.firstFrameUrl.isBlank()) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", task.firstFrameUrl)));
        }
        return content;
    }

    private JsonNode post(AiProviderConfigEntity config, String path, Map<String, Object> payload, String idempotencyKey) throws Exception {
        HttpRequest request = baseRequest(config, path, idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
        return response(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode get(AiProviderConfigEntity config, String path, String idempotencyKey) throws Exception {
        HttpRequest request = baseRequest(config, path, idempotencyKey).GET().build();
        return response(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private HttpRequest.Builder baseRequest(AiProviderConfigEntity config, String path, String idempotencyKey) {
        return HttpRequest.newBuilder(uri(config.getBaseUrl(), path))
            .header("Authorization", "Bearer " + secretCodec.requireDecrypted(config.getApiKeyCipher()))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey);
    }

    private JsonNode response(HttpResponse<String> response) throws Exception {
        JsonNode body;
        try {
            body = objectMapper.readTree(response.body());
        } catch (Exception exception) {
            body = objectMapper.createObjectNode();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = firstText(body, "code", "error_code");
            String message = firstText(body, "message", "error_message");
            if (body.path("error").isObject()) {
                if (code == null) code = firstText(body.path("error"), "code", "error_code");
                if (message == null) message = firstText(body.path("error"), "message", "error_message");
            }
            String detail = String.join(": ",
                java.util.stream.Stream.of(code, message)
                    .filter(value -> value != null && !value.isBlank())
                    .map(this::sanitizeDiagnostic)
                    .toList());
            throw new IllegalStateException(
                "火山方舟请求失败，HTTP " + response.statusCode() + (detail.isBlank() ? "" : "：" + detail));
        }
        return body;
    }

    private String sanitizeDiagnostic(String value) {
        String sanitized = value.replaceAll("([?&](?:token|api_key|access_key|secret_key)=)[^&\\s]+", "$1***");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private URI uri(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + path);
    }

    private String requireEndpointId(AiModelEntity model) {
        String endpointId = model.getModelCode();
        if (endpointId == null || endpointId.isBlank() || endpointId.startsWith("__SEEDANCE_")) {
            throw new IllegalStateException(ErrorCode.AI_MODEL_DISABLED.name() + ": Seedance Endpoint ID 尚未配置。");
        }
        return endpointId.trim();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "RUNNING";
        return switch (status.trim().toUpperCase()) {
            case "SUCCEEDED", "SUCCESS", "COMPLETED", "DONE" -> "SUCCEEDED";
            case "CANCELED", "CANCELLED" -> "CANCELED";
            case "FAILED", "ERROR", "EXPIRED", "REJECTED" -> "FAILED";
            default -> "RUNNING";
        };
    }
}
