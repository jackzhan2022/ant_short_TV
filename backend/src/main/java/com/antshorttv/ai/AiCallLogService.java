package com.antshorttv.ai;

import com.antshorttv.security.TenantContextResolver;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiCallLogService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextResolver tenantContextResolver;

    public AiCallLogService(JdbcTemplate jdbcTemplate, TenantContextResolver tenantContextResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContextResolver = tenantContextResolver;
    }

    public AiCallLogPageResponse list(
        Long tenantId,
        Integer current,
        Integer pageSize,
        String serviceType,
        String status,
        String businessScene
    ) {
        tenantContextResolver.requireActiveMember(tenantId);
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<Object> args = new ArrayList<>();
        String where = buildWhere(tenantId, serviceType, status, businessScene, args);

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log l " + where,
            Long.class,
            args.toArray()
        );

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safePageSize);
        pageArgs.add((safeCurrent - 1) * safePageSize);
        List<AiCallLogResponse> records = jdbcTemplate.query(
            """
                select
                  l.id,
                  l.tenant_id,
                  l.user_id,
                  l.task_id,
                  l.model_id,
                  l.provider_id,
                  l.provider,
                  l.service_type,
                  l.model,
                  l.business_scene,
                  l.request_summary,
                  l.response_summary,
                  l.status,
                  l.error_message,
                  l.duration_ms,
                  l.trace_id,
                  l.provider_request_id,
                  l.prompt_tokens,
                  l.completion_tokens,
                  l.total_tokens,
                  l.response_length,
                  l.finish_reason,
                  l.truncated,
                  l.created_at
                from ai_call_log l
                %s
                order by l.created_at desc, l.id desc
                limit ? offset ?
                """.formatted(where),
            (rs, rowNum) -> new AiCallLogResponse(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getObject("task_id", Long.class),
                rs.getObject("model_id", Long.class),
                rs.getObject("provider_id", Long.class),
                rs.getString("provider"),
                rs.getString("service_type"),
                rs.getString("model"),
                rs.getString("business_scene"),
                rs.getString("request_summary"),
                rs.getString("response_summary"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getLong("duration_ms"),
                rs.getString("trace_id"),
                rs.getString("provider_request_id"),
                rs.getObject("prompt_tokens", Integer.class),
                rs.getObject("completion_tokens", Integer.class),
                rs.getObject("total_tokens", Integer.class),
                rs.getObject("response_length", Integer.class),
                rs.getString("finish_reason"),
                rs.getObject("truncated", Boolean.class),
                toLocalDateTime(rs.getTimestamp("created_at"))
            ),
            pageArgs.toArray()
        );

        return new AiCallLogPageResponse(records, total == null ? 0 : total, safeCurrent, safePageSize);
    }

    private String buildWhere(
        Long tenantId,
        String serviceType,
        String status,
        String businessScene,
        List<Object> args
    ) {
        StringBuilder where = new StringBuilder("where l.tenant_id = ?");
        args.add(tenantId);
        appendEquals(where, args, "l.service_type", serviceType);
        appendEquals(where, args, "l.status", status);
        appendEquals(where, args, "l.business_scene", businessScene);
        return where.toString();
    }

    private void appendEquals(StringBuilder where, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" and ").append(column).append(" = ?");
        args.add(value.trim());
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
