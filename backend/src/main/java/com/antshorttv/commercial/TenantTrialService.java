package com.antshorttv.commercial;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.points.PointAccountingService;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantTrialService {
    private final JdbcTemplate jdbc;
    private final PointAccountingService accounting;

    public TenantTrialService(JdbcTemplate jdbc, PointAccountingService accounting) {
        this.jdbc = jdbc;
        this.accounting = accounting;
    }

    @Transactional
    public void assign(Long tenantId, Long userId) {
        var tenants = jdbc.queryForList("select id from tenant where id=? and deleted_at is null for update", Long.class, tenantId);
        if (tenants.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "租户不存在。");
        accounting.lockAccount(tenantId);
        if (jdbc.queryForObject("select count(*) from point_ledger where tenant_id=? and idempotency_key=?",
            Integer.class, tenantId, "trial:" + tenantId) > 0) return;
        if (jdbc.queryForObject("select count(*) from team_subscription where tenant_id=? and status in ('ACTIVE','QUEUED')",
            Integer.class, tenantId) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "已有套餐的租户不可分配试用套餐。");
        }
        Long versionId = jdbc.queryForObject("select v.id from commercial_package_version v join commercial_package p on p.id=v.package_id where p.code='TENANT_TRIAL'", Long.class);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            insert into team_subscription
              (tenant_id,package_version_id,source_order_id,status,starts_at,ends_at,snapshot_json,created_at,updated_at)
            values (?,?,0,'ACTIVE',?,?,'{"name":"试用套餐"}',?,?)
            """, tenantId, versionId, now, LocalDateTime.of(9999, 12, 31, 0, 0), now, now);
        accounting.grantTrial(tenantId, userId);
    }
}
