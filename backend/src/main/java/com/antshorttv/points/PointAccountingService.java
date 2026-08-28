package com.antshorttv.points;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single transactional boundary for account snapshots and the append-only ledger. */
@Service
@Transactional
public class PointAccountingService {
    private final JdbcTemplate jdbc;
    private final AiPointLedgerMapper ledgerMapper;

    public PointAccountingService(JdbcTemplate jdbc, AiPointLedgerMapper ledgerMapper) {
        this.jdbc = jdbc;
        this.ledgerMapper = ledgerMapper;
    }

    public void ensureAccount(Long tenantId) {
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, reserved_balance,
               total_reserved, total_released, total_refunded, version, created_at, updated_at)
            select ?, 0, 0, 0, 0, 0, 0, 0, 0, now(), now()
             where not exists (select 1 from team_point_account where tenant_id = ?)
            """, tenantId, tenantId);
    }

    public int reserveFunds(Long tenantId, BigDecimal amount) {
        return jdbc.update("""
            update team_point_account
               set balance = balance - ?, reserved_balance = reserved_balance + ?,
                   total_reserved = total_reserved + ?, version = version + 1, updated_at = now()
             where tenant_id = ? and balance >= ?
            """, amount, amount, amount, tenantId, amount);
    }

    public int incrementReserveFunds(Long tenantId, BigDecimal amount) {
        return reserveFunds(tenantId, amount);
    }

    public void settleFunds(Long tenantId, BigDecimal reserved, BigDecimal released, BigDecimal consumed) {
        jdbc.update("""
            update team_point_account
               set reserved_balance = reserved_balance - ?, balance = balance + ?,
                   total_consumed = total_consumed + ?, total_released = total_released + ?,
                   version = version + 1, updated_at = now()
             where tenant_id = ?
            """, reserved, released, consumed, released, tenantId);
    }

    public void releaseFunds(Long tenantId, BigDecimal amount) {
        jdbc.update("""
            update team_point_account
               set reserved_balance = reserved_balance - ?, balance = balance + ?,
                   total_released = total_released + ?, version = version + 1, updated_at = now()
             where tenant_id = ?
            """, amount, amount, amount, tenantId);
    }

    public void refundFunds(Long tenantId, BigDecimal amount) {
        jdbc.update("""
            update team_point_account
               set balance = balance + ?, total_refunded = total_refunded + ?,
                   version = version + 1, updated_at = now()
             where tenant_id = ?
            """, amount, amount, tenantId);
    }

    public AiPointLedgerEntity append(AiPointLedgerEntity entry) {
        AiPointLedgerEntity existing = find(entry.tenantId, entry.idempotencyKey);
        if (existing != null) {
            if (!existing.entryType.equals(entry.entryType) || existing.amount.compareTo(entry.amount) != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分幂等键已用于不同的账务命令。");
            }
            return existing;
        }
        Map<String, Object> account = jdbc.queryForMap(
            "select balance, reserved_balance from team_point_account where tenant_id = ?", entry.tenantId);
        entry.availableBalanceAfter = decimal(account.get("balance"));
        entry.reservedBalanceAfter = decimal(account.get("reserved_balance"));
        entry.createdAt = entry.createdAt == null ? LocalDateTime.now() : entry.createdAt;
        ledgerMapper.insert(entry);
        return entry;
    }

    public void grant(Long tenantId, Long userId, BigDecimal amount, String key, String description) {
        ensureAccount(tenantId);
        if (amount.signum() <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分数量必须大于 0。");
        AiPointLedgerEntity existing = find(tenantId, key);
        if (existing != null) {
            requireSame(existing, "GRANT", amount);
            return;
        }
        jdbc.update("update team_point_account set balance=balance+?, total_granted=total_granted+?, version=version+1, updated_at=now() where tenant_id=?", amount, amount, tenantId);
        AiPointLedgerEntity e = base(tenantId, userId, "GRANT", amount, key, description);
        append(e);
    }

    private AiPointLedgerEntity base(Long tenantId, Long userId, String type, BigDecimal amount, String key, String description) {
        AiPointLedgerEntity e = new AiPointLedgerEntity(); e.tenantId=tenantId; e.userId=userId; e.entryType=type; e.amount=amount; e.idempotencyKey=key; e.description=description; return e;
    }
    private AiPointLedgerEntity find(Long tenantId, String key) {
        return ledgerMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiPointLedgerEntity>()
            .eq("tenant_id", tenantId).eq("idempotency_key", key));
    }
    private void requireSame(AiPointLedgerEntity existing, String type, BigDecimal amount) {
        if (!type.equals(existing.entryType) || existing.amount.compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分幂等键已用于不同的账务命令。");
        }
    }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(value)); }
}
