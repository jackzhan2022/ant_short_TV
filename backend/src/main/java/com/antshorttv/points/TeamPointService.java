package com.antshorttv.points;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamPointService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;
    private final PointAccountingService accountingService;

    public TeamPointService(
        JdbcTemplate jdbcTemplate,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService,
        PointAccountingService accountingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
        this.accountingService = accountingService;
    }

    @Transactional
    public TeamPointAccountResponse account(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        accountingService.ensureAccount(tenantId);
        return readAccount(tenantId);
    }

    @Transactional
    public TeamPointAccountResponse adjust(
        Long tenantId,
        TeamPointAdjustmentRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireOwner(tenantId);
        int amount = request.amount();
        if (amount == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分调整数量不能为 0。");
        }
        accountingService.ensureAccount(tenantId);
        String idempotencyKey = servletRequest.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = "admin-adjust:" + UUID.randomUUID();
        }
        if (amount > 0) {
            accountingService.grant(tenantId, context.userId(), BigDecimal.valueOf(amount), idempotencyKey, request.description());
        } else {
            accountingService.adjustDebit(tenantId, context.userId(), BigDecimal.valueOf(-amount), idempotencyKey, request.description());
        }
        operationLogService.record(context.userId(), tenantId, "ADJUST_TEAM_POINTS", tenantId, OperationResult.SUCCESS, servletRequest);
        return readAccount(tenantId);
    }

    public TeamPointTransactionPageResponse transactions(Long tenantId, Integer current, Integer pageSize) {
        tenantContextResolver.requireActiveMember(tenantId);
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Long total = jdbcTemplate.queryForObject(
            "select count(*) from point_ledger where tenant_id = ?",
            Long.class,
            tenantId
        );
        List<TeamPointTransactionResponse> records = jdbcTemplate.query("""
            select id, tenant_id, user_id, entry_type, amount, available_balance_after,
                   business_type, business_id, description, created_at
              from point_ledger
             where tenant_id = ?
             order by created_at desc, id desc
             limit ? offset ?
            """,
            (rs, rowNum) -> new TeamPointTransactionResponse(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                responseType(rs.getString("entry_type")),
                rs.getBigDecimal("amount").intValue(),
                rs.getBigDecimal("available_balance_after").intValue(),
                rs.getString("business_type"),
                rs.getObject("business_id", Long.class),
                rs.getString("description"),
                toLocalDateTime(rs.getTimestamp("created_at"))
            ),
            tenantId,
            safePageSize,
            (safeCurrent - 1) * safePageSize
        );
        return new TeamPointTransactionPageResponse(records, total == null ? 0 : total, safeCurrent, safePageSize);
    }

    private TeamPointAccountResponse readAccount(Long tenantId) {
        return jdbcTemplate.queryForObject("""
            select tenant_id, balance, total_granted, total_consumed, updated_at
              from team_point_account
             where tenant_id = ?
            """,
            (rs, rowNum) -> new TeamPointAccountResponse(
                rs.getLong("tenant_id"),
                rs.getInt("balance"),
                rs.getInt("total_granted"),
                rs.getInt("total_consumed"),
                toLocalDateTime(rs.getTimestamp("updated_at"))
            ),
            tenantId
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String responseType(String entryType) {
        return "GRANT".equals(entryType) ? "ADJUST_GRANT" : entryType;
    }
}
