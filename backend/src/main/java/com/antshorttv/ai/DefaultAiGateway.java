package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiGateway extends AiGateway {
    private final AiModelRouter aiModelRouter;
    private final JdbcTemplate jdbcTemplate;

    public DefaultAiGateway(AiModelRouter aiModelRouter, JdbcTemplate jdbcTemplate) {
        this.aiModelRouter = aiModelRouter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AiTextResponse text(AiContext context, AiTextRequest request) {
        validateContext(context);
        AiModelRoute route = aiModelRouter.route(context.modelId(), "TEXT");
        long started = System.currentTimeMillis();
        try {
            AiTextResponse response = route.adapter().text(route.provider(), route.providerConfig(), route.model(), request);
            record(context.withModelId(route.model().getId()), route, "TEXT", request.userPrompt(), response.content(), "SUCCESS", null, started, response);
            return response;
        } catch (AiGatewayException exception) {
            record(context.withModelId(route.model().getId()), route, "TEXT", request.userPrompt(), null, "FAILED", exception.getMessage(), started, null);
            throw exception;
        }
    }

    @Override
    public AiImageResponse image(AiContext context, AiImageRequest request) {
        validateContext(context);
        AiModelRoute route = aiModelRouter.route(context.modelId(), "IMAGE");
        long started = System.currentTimeMillis();
        try {
            AiImageResponse response = route.adapter().image(route.provider(), route.providerConfig(), route.model(), request);
            record(context.withModelId(route.model().getId()), route, "IMAGE", request.prompt(), "generated=%d".formatted(response.imageUrls().size()), "SUCCESS", null, started, response);
            return response;
        } catch (AiGatewayException exception) {
            record(context.withModelId(route.model().getId()), route, "IMAGE", request.prompt(), null, "FAILED", exception.getMessage(), started, null);
            throw exception;
        }
    }

    private void validateContext(AiContext context) {
        if (context == null || context.tenantId() == null || context.userId() == null || context.projectId() == null) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 调用上下文不完整。");
        }
    }

    private Long record(
        AiContext context,
        AiModelRoute route,
        String serviceType,
        String requestSummary,
        String responseSummary,
        String status,
        String errorMessage,
        long started,
        Object response
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                insert into ai_call_log
                  (tenant_id, user_id, service_config_id, provider, service_type, model, business_scene,
                   request_summary, response_summary, status, error_message, duration_ms, created_at,
                   task_id, model_id, provider_id, trace_id, provider_request_id, prompt_tokens,
                   completion_tokens, total_tokens, estimated_cost)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, context.tenantId());
            ps.setLong(2, context.userId());
            if (route.model().getLegacyServiceConfigId() == null) {
                ps.setObject(3, null);
            } else {
                ps.setLong(3, route.model().getLegacyServiceConfigId());
            }
            ps.setString(4, route.provider().getCode());
            ps.setString(5, serviceType);
            ps.setString(6, route.model().getName());
            ps.setString(7, context.businessType());
            ps.setString(8, trimSummary(requestSummary));
            ps.setString(9, trimSummary(responseSummary));
            ps.setString(10, status);
            ps.setString(11, trimSummary(errorMessage));
            ps.setLong(12, Math.max(1, System.currentTimeMillis() - started));
            ps.setObject(13, LocalDateTime.now());
            ps.setObject(14, context.taskId());
            ps.setLong(15, context.modelId());
            ps.setLong(16, route.provider().getId());
            ps.setString(17, context.traceId());
            ps.setString(18, providerRequestId(response));
            ps.setObject(19, tokens(response, "prompt"));
            ps.setObject(20, tokens(response, "completion"));
            ps.setObject(21, tokens(response, "total"));
            ps.setBigDecimal(22, BigDecimal.ZERO);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private String providerRequestId(Object response) {
        if (response instanceof AiTextResponse textResponse) {
            return textResponse.providerRequestId();
        }
        if (response instanceof AiImageResponse imageResponse) {
            return imageResponse.providerRequestId();
        }
        return null;
    }

    private Integer tokens(Object response, String type) {
        if (response instanceof AiTextResponse textResponse) {
            return switch (type) {
                case "prompt" -> textResponse.promptTokens();
                case "completion" -> textResponse.completionTokens();
                case "total" -> textResponse.totalTokens();
                default -> null;
            };
        }
        return null;
    }

    private String trimSummary(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
