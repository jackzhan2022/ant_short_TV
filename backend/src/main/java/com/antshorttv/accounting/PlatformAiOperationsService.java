package com.antshorttv.accounting;

import com.antshorttv.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import static com.antshorttv.accounting.PlatformAiOperationsResponses.PlatformAiOperationsOverview;
import static com.antshorttv.accounting.PlatformAiOperationsResponses.ProviderFailureRate;

@Service
public class PlatformAiOperationsService {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentPrincipal currentPrincipal;

    public PlatformAiOperationsService(JdbcTemplate jdbcTemplate, CurrentPrincipal currentPrincipal) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentPrincipal = currentPrincipal;
    }

    public PlatformAiOperationsOverview overview() {
        currentPrincipal.require();
        return new PlatformAiOperationsOverview(
            scalar("select count(*) from ai_execution_task where status = 'RUNNING' and claim_expires_at < now()"),
            scalar("select count(*) from ai_execution_task where status = 'FAILED' and retryable = false"),
            scalar("select count(*) from ai_usage_cost_line where pricing_status = 'UNPRICED'"),
            scalar("select count(*) from ai_usage_cost_line where pricing_status = 'INCOMPLETE'"),
            scalar("select count(*) from ai_point_reservation where status = 'SETTLEMENT_REVIEW_REQUIRED'"),
            decimal("select coalesce(sum(rounded_cost), 0) from ai_usage_cost_line where pricing_status = 'PRICED'"),
            decimal("select coalesce(sum(settled_points), 0) from ai_point_reservation"),
            jdbcTemplate.query("""
                select provider,
                       count(*) total,
                       sum(case when status = 'FAILED' then 1 else 0 end) failed
                  from ai_call_log
                 group by provider
                 order by provider
                """, (rs, rowNum) -> {
                long total = rs.getLong("total");
                long failed = rs.getLong("failed");
                BigDecimal rate = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(failed)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
                return new ProviderFailureRate(rs.getString("provider"), total, failed, rate);
            })
        );
    }

    private long scalar(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}
