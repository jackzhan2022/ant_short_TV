package com.antshorttv.video;

import com.antshorttv.ai.AiContext;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiModelRoute;
import com.antshorttv.ai.AiModelRouter;
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
public class VideoUnderstandingGateway {
    private final AiModelRouter aiModelRouter;
    private final QwenVideoUnderstandingAdapter qwenAdapter;
    private final JdbcTemplate jdbcTemplate;

    public VideoUnderstandingGateway(
        AiModelRouter aiModelRouter,
        QwenVideoUnderstandingAdapter qwenAdapter,
        JdbcTemplate jdbcTemplate
    ) {
        this.aiModelRouter = aiModelRouter;
        this.qwenAdapter = qwenAdapter;
        this.jdbcTemplate = jdbcTemplate;
    }

    public VideoUnderstandingCallResult call(AiContext context, VideoUnderstandingRequest request) {
        validateContext(context);
        AiModelRoute route = aiModelRouter.route(context.modelId(), "VIDEO_UNDERSTANDING");
        long started = System.currentTimeMillis();
        try {
            VideoUnderstandingResponse response = qwenAdapter.videoUnderstanding(
                route.provider(),
                route.providerConfig(),
                route.model(),
                request
            );
            Long callLogId = record(
                context.withModelId(route.model().getId()),
                route,
                request.videoUrl(),
                response.content(),
                "SUCCESS",
                null,
                started,
                response
            );
            return new VideoUnderstandingCallResult(response, callLogId);
        } catch (AiGatewayException exception) {
            record(
                context.withModelId(route.model().getId()),
                route,
                request.videoUrl(),
                null,
                "FAILED",
                exception.getMessage(),
                started,
                null
            );
            throw exception;
        }
    }

    public void markBusinessFailure(Long callLogId, String errorMessage) {
        if (callLogId == null) {
            return;
        }
        jdbcTemplate.update("""
            update ai_call_log
               set status = 'FAILED',
                   error_message = ?
             where id = ?
            """, trimSummary("业务解析失败：" + errorMessage), callLogId);
    }

    private void validateContext(AiContext context) {
        if (context == null || context.tenantId() == null || context.userId() == null || context.projectId() == null) {
            throw new AiGatewayException(ErrorCode.VALIDATION_ERROR, "AI 调用上下文不完整。");
        }
    }

    private Long record(
        AiContext context,
        AiModelRoute route,
        String requestSummary,
        String responseSummary,
        String status,
        String errorMessage,
        long started,
        VideoUnderstandingResponse response
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
            ps.setObject(3, route.model().getLegacyServiceConfigId());
            ps.setString(4, route.provider().getCode());
            ps.setString(5, "VIDEO_UNDERSTANDING");
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
            ps.setString(18, response == null ? null : response.providerRequestId());
            ps.setObject(19, response == null ? null : response.promptTokens());
            ps.setObject(20, response == null ? null : response.completionTokens());
            ps.setObject(21, response == null ? null : response.totalTokens());
            ps.setBigDecimal(22, BigDecimal.ZERO);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private String trimSummary(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
