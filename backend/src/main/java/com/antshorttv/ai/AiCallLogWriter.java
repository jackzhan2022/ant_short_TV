package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiCallLogWriter {
    private final JdbcTemplate jdbcTemplate;

    public AiCallLogWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long record(AiInvocationLogRequest logRequest) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                insert into ai_call_log
                  (tenant_id, user_id, provider, service_type, model, business_scene,
                   request_summary, response_summary, status, error_message, duration_ms, created_at,
                   task_id, model_id, provider_id, trace_id, provider_request_id, prompt_tokens,
                   completion_tokens, total_tokens, estimated_cost,
                   execution_id, attempt_id, execution_version, phase, idempotency_key,
                   external_task_id, transport_outcome, business_outcome)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            AiContext context = logRequest.context();
            AiModelRoute route = logRequest.route();
            ps.setLong(1, context.tenantId());
            ps.setLong(2, context.userId());
            ps.setString(3, route == null ? null : route.provider().getCode());
            ps.setString(4, logRequest.capability().modelServiceType());
            ps.setString(5, route == null ? null : route.model().getName());
            ps.setString(6, context.businessType());
            ps.setString(7, sanitizeSummary(logRequest.requestSummary(), context.businessType()));
            ps.setString(8, sanitizeSummary(logRequest.responseSummary(), context.businessType()));
            ps.setString(9, logRequest.status());
            ps.setString(10, sanitizeSummary(logRequest.errorMessage(), context.businessType()));
            ps.setLong(11, Math.max(1, logRequest.durationMs() == null ? 1 : logRequest.durationMs()));
            ps.setObject(12, LocalDateTime.now());
            ps.setObject(13, context.taskId());
            ps.setObject(14, route == null ? context.modelId() : route.model().getId());
            ps.setObject(15, route == null ? null : route.provider().getId());
            ps.setString(16, context.traceId());
            ps.setString(17, logRequest.providerRequestId());
            ps.setObject(18, logRequest.promptTokens());
            ps.setObject(19, logRequest.completionTokens());
            ps.setObject(20, logRequest.totalTokens());
            ps.setBigDecimal(21, BigDecimal.ZERO);
            ps.setObject(22, context.executionId());
            ps.setObject(23, context.attemptId());
            ps.setObject(24, context.executionVersion());
            ps.setString(25, context.phase());
            ps.setString(26, context.idempotencyKey());
            ps.setString(27, logRequest.externalTaskId());
            ps.setString(28, logRequest.transportOutcome());
            ps.setString(29, logRequest.businessOutcome());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void markBusinessFailure(Long callLogId, ErrorCode errorCode, String errorMessage) {
        if (callLogId == null) {
            return;
        }
        jdbcTemplate.update("""
            update ai_call_log
               set status = 'FAILED',
                   error_message = ?,
                   business_outcome = 'FAILED'
             where id = ?
            """, trimSummary("业务解析失败：" + errorCode.name() + " " + errorMessage), callLogId);
    }

    private String trimSummary(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }

    private String sanitizeSummary(String value, String scene) {
        if (value == null) {
            return null;
        }
        String sanitized = value
            .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
            .replaceAll("(?i)(api[_-]?key|access[_-]?key|secret|password)[\\\"']?\\s*[:=]\\s*[\\\"']?[^,;\\s\\\"'}]+", "$1=[REDACTED]")
            .replaceAll("(?i)sk-[A-Za-z0-9._-]+", "[REDACTED]");
        return trimSummary(sanitized);
    }
}
