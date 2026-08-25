package com.antshorttv.video;

import com.antshorttv.ai.AiProviderExecutionOutcome;
import com.antshorttv.ai.AiProviderReconciliationStatus;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiProviderConfigEntity;
import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiVideoProviderAdapter {
    private final AiSecretCodec secretCodec;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiVideoProviderAdapter(AiSecretCodec secretCodec, ObjectMapper objectMapper) {
        this.secretCodec = secretCodec;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AiProviderExecutionOutcome<VideoResult> submit(
        AiProviderConfigEntity providerConfig,
        AiModelEntity model,
        AiVideoTaskEntity task,
        String idempotencyKey
    ) throws Exception {
        String endpoint = modelEndpoint(model, "submitEndpoint", "/video/generations");
        String payload = objectMapper.writeValueAsString(Map.of(
            "model", task.model,
            "prompt", task.prompt,
            "negativePrompt", task.negativePrompt == null ? "" : task.negativePrompt,
            "firstFrameUrl", task.firstFrameUrl,
            "durationSeconds", task.durationSeconds,
            "aspectRatio", task.aspectRatio,
            "resolution", task.resolution == null ? "STANDARD" : task.resolution,
            "cameraMovement", task.cameraMovement == null ? "" : task.cameraMovement,
            "motionStrength", task.motionStrength == null ? "MEDIUM" : task.motionStrength,
            "randomSeed", task.randomSeed == null ? "" : task.randomSeed
        ));
        JsonNode body = sendJson(providerConfig, endpoint, payload, idempotencyKey);
        String externalTaskId = firstText(body, "externalTaskId", "external_task_id", "taskId", "task_id", "id");
        if (externalTaskId == null) {
            throw new IllegalStateException("服务商未返回任务 ID。");
        }
        return AiProviderExecutionOutcome.accepted(
            firstText(body, "requestId", "request_id"),
            externalTaskId,
            Duration.ofSeconds(10),
            AiProviderReconciliationStatus.NOT_REQUIRED
        );
    }

    public AiProviderExecutionOutcome<VideoResult> poll(
        AiProviderConfigEntity providerConfig,
        AiModelEntity model,
        String externalTaskId,
        String idempotencyKey
    ) throws Exception {
        String endpoint = modelEndpoint(model, "queryEndpoint", "/video/tasks");
        JsonNode body = sendJson(
            providerConfig,
            endpoint,
            objectMapper.writeValueAsString(Map.of("externalTaskId", externalTaskId)),
            idempotencyKey
        );
        String status = normalizeStatus(firstText(body, "status", "externalStatus", "external_status"));
        String videoUrl = firstText(body, "videoUrl", "video_url", "url");
        String errorMessage = firstText(body, "errorMessage", "error_message", "message");
        if ("SUCCEEDED".equals(status) && videoUrl == null) {
            return AiProviderExecutionOutcome.completed(
                new VideoResult("FAILED", null, "未生成有效视频。"),
                firstText(body, "requestId", "request_id")
            );
        }
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
            return AiProviderExecutionOutcome.completed(
                new VideoResult(status, videoUrl, errorMessage),
                firstText(body, "requestId", "request_id")
            );
        }
        return AiProviderExecutionOutcome.accepted(
            firstText(body, "requestId", "request_id"),
            externalTaskId,
            Duration.ofSeconds(10),
            AiProviderReconciliationStatus.NOT_REQUIRED
        );
    }

    public byte[] download(String externalVideoUrl) throws Exception {
        URI uri = URI.create(externalVideoUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("外部视频地址不可下载。");
        }
        HttpResponse<byte[]> response = httpClient.send(
            HttpRequest.newBuilder(uri).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0) {
            throw new IllegalStateException("视频下载失败，HTTP " + response.statusCode());
        }
        return response.body();
    }

    private JsonNode sendJson(
        AiProviderConfigEntity providerConfig,
        String endpoint,
        String payload,
        String idempotencyKey
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(buildUri(providerConfig.getBaseUrl(), endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + secretCodec.requireDecrypted(providerConfig.getApiKeyCipher()))
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private URI buildUri(String baseUrl, String endpoint) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return URI.create(base + path);
    }

    private String modelEndpoint(AiModelEntity model, String field, String defaultEndpoint) throws Exception {
        if (model.getConfigJson() == null || model.getConfigJson().isBlank()) {
            return defaultEndpoint;
        }
        JsonNode value = objectMapper.readTree(model.getConfigJson()).get(field);
        return value == null || value.asText().isBlank() ? defaultEndpoint : value.asText().trim();
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
            case "SUCCESS", "SUCCEEDED", "COMPLETED", "DONE", "FINISHED" -> "SUCCEEDED";
            case "FAILED", "ERROR", "CANCELED", "CANCELLED", "REJECTED" -> "FAILED";
            default -> "RUNNING";
        };
    }

    public record VideoResult(String status, String videoUrl, String errorMessage) {
    }
}
