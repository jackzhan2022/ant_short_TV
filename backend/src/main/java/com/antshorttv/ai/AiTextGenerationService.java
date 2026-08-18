package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class AiTextGenerationService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final AiSecretCodec aiSecretCodec;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiTextGenerationService(
        AiServiceConfigMapper aiServiceConfigMapper,
        AiSecretCodec aiSecretCodec,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.aiSecretCodec = aiSecretCodec;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    public AiTextGenerationResponse generateText(AiTextGenerationRequest request) {
        return generate(request, false);
    }

    public JsonNode generateJson(AiTextGenerationRequest request) {
        AiTextGenerationResponse response = generate(request, true);
        try {
            return objectMapper.readTree(response.content());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型返回的内容不是有效 JSON。");
        }
    }

    private AiTextGenerationResponse generate(AiTextGenerationRequest request, boolean jsonMode) {
        AiServiceConfigEntity config = resolveTextService(request.tenantId());
        validateConfig(config);
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            HttpRequest httpRequest = buildRequest(config, request, jsonMode);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            LocalDateTime finishedAt = LocalDateTime.now();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "AI 文本服务请求失败，HTTP %d。".formatted(response.statusCode());
                recordCall(request, config, "FAILED", trimSummary(response.body()), message, startedAt, finishedAt);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
            }
            String content = extractContent(config, response.body());
            if (content == null || content.isBlank()) {
                String message = "模型未返回有效内容。";
                recordCall(request, config, "FAILED", trimSummary(response.body()), message, startedAt, finishedAt);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
            }
            Long callLogId = recordCall(request, config, "SUCCESS", trimSummary(content), null, startedAt, finishedAt);
            return new AiTextGenerationResponse(callLogId, content.trim());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            LocalDateTime finishedAt = LocalDateTime.now();
            recordCall(request, config, "FAILED", null, exception.getMessage(), startedAt, finishedAt);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 文本服务调用失败：" + exception.getMessage());
        }
    }

    private HttpRequest buildRequest(AiServiceConfigEntity config, AiTextGenerationRequest request, boolean jsonMode) throws Exception {
        String endpoint = resolveEndpoint(config);
        String baseUrl = trimTrailingSlash(config.getBaseUrl());
        URI uri = URI.create(baseUrl + endpoint);
        String payload = isGemini(config)
            ? buildGeminiPayload(request, jsonMode)
            : buildOpenAiPayload(config, request, jsonMode);
        return HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + aiSecretCodec.decrypt(config.getApiKeyCipher()))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    }

    private String buildOpenAiPayload(AiServiceConfigEntity config, AiTextGenerationRequest request, boolean jsonMode) throws Exception {
        Map<String, Object> payload = Map.of(
            "model", config.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", promptPrefix(request.systemPrompt(), jsonMode)),
                Map.of("role", "user", "content", promptPrefix(request.userPrompt(), jsonMode))
            )
        );
        return objectMapper.writeValueAsString(payload);
    }

    private String buildGeminiPayload(AiTextGenerationRequest request, boolean jsonMode) throws Exception {
        Map<String, Object> payload = Map.of(
            "contents", List.of(
                Map.of(
                    "parts", List.of(
                        Map.of("text", promptPrefix(request.systemPrompt(), jsonMode) + "\n\n" + promptPrefix(request.userPrompt(), jsonMode))
                    )
                )
            )
        );
        return objectMapper.writeValueAsString(payload);
    }

    private String extractContent(AiServiceConfigEntity config, String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (isGemini(config)) {
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    for (JsonNode part : parts) {
                        String text = part.path("text").asText(null);
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
            return root.path("text").asText(null);
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            String text = message.path("content").asText(null);
            if (text != null && !text.isBlank()) {
                return text;
            }
            text = choices.get(0).path("text").asText(null);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return root.path("text").asText(null);
    }

    private Long recordCall(
        AiTextGenerationRequest request,
        AiServiceConfigEntity config,
        String status,
        String responseSummary,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
    ) {
        Long durationMs = Math.max(1L, Duration.between(startedAt, finishedAt).toMillis());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                insert into ai_call_log
                  (tenant_id, user_id, service_config_id, provider, service_type, model, business_scene, request_summary, response_summary, status, error_message, duration_ms, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, request.tenantId());
            statement.setLong(2, request.userId());
            statement.setLong(3, config.getId());
            statement.setString(4, config.getProvider());
            statement.setString(5, config.getServiceType());
            statement.setString(6, config.getModel());
            statement.setString(7, request.businessScene());
            statement.setString(8, trimSummary(request.requestSummary()));
            statement.setString(9, responseSummary);
            statement.setString(10, status);
            statement.setString(11, errorMessage);
            statement.setLong(12, durationMs);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private AiServiceConfigEntity resolveTextService(Long tenantId) {
        AiServiceConfigEntity config = aiServiceConfigMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigEntity>()
            .eq(AiServiceConfigEntity::getTenantId, tenantId)
            .eq(AiServiceConfigEntity::getServiceType, "TEXT")
            .eq(AiServiceConfigEntity::getEnabled, true)
            .isNull(AiServiceConfigEntity::getDeletedAt)
            .orderByDesc(AiServiceConfigEntity::getIsDefault)
            .orderByDesc(AiServiceConfigEntity::getPriority)
            .last("limit 1"));
        if (config == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前团队未配置可用文本服务。");
        }
        return config;
    }

    private void validateConfig(AiServiceConfigEntity config) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 文本服务接口地址不能为空。");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 文本服务模型不能为空。");
        }
    }

    private boolean isGemini(AiServiceConfigEntity config) {
        return config.getProvider() != null && config.getProvider().equalsIgnoreCase("Gemini");
    }

    private String resolveEndpoint(AiServiceConfigEntity config) {
        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            return normalizeEndpoint(config.getEndpoint());
        }
        if (isGemini(config)) {
            return "/v1beta/models/" + config.getModel() + ":generateContent";
        }
        return "/chat/completions";
    }

    private String normalizeEndpoint(String endpoint) {
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String promptPrefix(String value, boolean jsonMode) {
        String base = value == null ? "" : value.trim();
        if (!jsonMode) {
            return base;
        }
        if (base.isBlank()) {
            return "请只输出有效 JSON，不要输出解释。";
        }
        return base + "\n\n请只输出有效 JSON，不要输出解释。";
    }

    private String trimSummary(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
}
