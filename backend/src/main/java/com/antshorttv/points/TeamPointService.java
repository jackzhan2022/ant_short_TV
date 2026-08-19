package com.antshorttv.points;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamPointService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;

    public TeamPointService(
        JdbcTemplate jdbcTemplate,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public TeamPointAccountResponse account(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        ensureAccount(tenantId);
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
        ensureAccount(tenantId);
        if (amount > 0) {
            jdbcTemplate.update("""
                update team_point_account
                   set balance = balance + ?,
                       total_granted = total_granted + ?,
                       updated_at = now()
                 where tenant_id = ?
                """, amount, amount, tenantId);
            recordTransaction(tenantId, context.userId(), "ADJUST_GRANT", amount, null, null, request.description());
        } else {
            consume(tenantId, context.userId(), -amount, "ADJUST_DEDUCT", null, null, request.description());
        }
        operationLogService.record(context.userId(), tenantId, "ADJUST_TEAM_POINTS", tenantId, OperationResult.SUCCESS, servletRequest);
        return readAccount(tenantId);
    }

    @Transactional
    public Long consumeForAi(TenantContext context, int amount, String businessScene, Long businessId, String description) {
        return consume(context.tenantId(), context.userId(), amount, "AI_CONSUME", businessScene, businessId, description);
    }

    public TeamPointTransactionPageResponse transactions(Long tenantId, Integer current, Integer pageSize) {
        tenantContextResolver.requireActiveMember(tenantId);
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Long total = jdbcTemplate.queryForObject(
            "select count(*) from team_point_transaction where tenant_id = ?",
            Long.class,
            tenantId
        );
        List<TeamPointTransactionResponse> records = jdbcTemplate.query("""
            select id, tenant_id, user_id, transaction_type, change_amount, balance_after,
                   business_scene, business_id, description, created_at
              from team_point_transaction
             where tenant_id = ?
             order by created_at desc, id desc
             limit ? offset ?
            """,
            (rs, rowNum) -> new TeamPointTransactionResponse(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("transaction_type"),
                rs.getInt("change_amount"),
                rs.getInt("balance_after"),
                rs.getString("business_scene"),
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

    private Long consume(
        Long tenantId,
        Long userId,
        int amount,
        String transactionType,
        String businessScene,
        Long businessId,
        String description
    ) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分消耗数量必须大于 0。");
        }
        ensureAccount(tenantId);
        int updated = jdbcTemplate.update("""
            update team_point_account
               set balance = balance - ?,
                   total_consumed = total_consumed + ?,
                   updated_at = now()
             where tenant_id = ?
               and balance >= ?
            """, amount, amount, tenantId, amount);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TEAM_POINTS_INSUFFICIENT, "团队积分不足，请充值后再使用 AI 能力。");
        }
        return recordTransaction(tenantId, userId, transactionType, -amount, businessScene, businessId, description);
    }

    private void ensureAccount(Long tenantId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from team_point_account where tenant_id = ?",
            Integer.class,
            tenantId
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, 0, 0, 0, now(), now())
            """, tenantId);
    }

    private Long recordTransaction(
        Long tenantId,
        Long userId,
        String transactionType,
        int changeAmount,
        String businessScene,
        Long businessId,
        String description
    ) {
        TeamPointAccountResponse account = readAccount(tenantId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                insert into team_point_transaction
                  (tenant_id, user_id, transaction_type, change_amount, balance_after,
                   business_scene, business_id, description, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setLong(2, userId);
            ps.setString(3, transactionType);
            ps.setInt(4, changeAmount);
            ps.setInt(5, account.balance());
            ps.setString(6, businessScene);
            if (businessId == null) {
                ps.setObject(7, null);
            } else {
                ps.setLong(7, businessId);
            }
            ps.setString(8, blankToNull(description));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
