package com.antshorttv.points;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.security.TenantContextResolver;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamPointService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TenantContextResolver tenantContextResolver;
    private final PointAccountingService accountingService;

    public TeamPointService(
        JdbcTemplate jdbcTemplate,
        TenantContextResolver tenantContextResolver,
        PointAccountingService accountingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContextResolver = tenantContextResolver;
        this.accountingService = accountingService;
    }

    @Transactional
    public TeamPointAccountResponse account(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        accountingService.ensureAccount(tenantId);
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
            select l.id, l.tenant_id, l.user_id, l.entry_type, l.amount, l.available_balance_after,
                   l.business_type, l.business_id, l.idempotency_key, l.description, l.created_at,
                   e.id as usage_execution_id, p.name as project_name,
                   coalesce(e.scene, r.scene) as usage_scene,
                   coalesce(e.business_type, r.business_type, l.business_type) as usage_business_type,
                   coalesce(e.business_id, r.business_id, l.business_id) as usage_business_id
              from point_ledger l
              left join ai_point_reservation r on r.id = l.reservation_id and r.tenant_id = l.tenant_id
              left join ai_execution_task e on e.id = coalesce(l.execution_id, r.execution_id)
                  and e.tenant_id = l.tenant_id
              left join project p on p.id = e.project_id and p.tenant_id = l.tenant_id
             where l.tenant_id = ?
             order by l.created_at desc, l.id desc
             limit ? offset ?
            """,
            (rs, rowNum) -> new TeamPointTransactionResponse(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("user_id"),
                responseType(rs.getString("entry_type"), rs.getString("idempotency_key")),
                responseAmount(rs.getString("entry_type"), rs.getBigDecimal("amount")),
                rs.getBigDecimal("available_balance_after").intValue(),
                rs.getString("business_type"),
                rs.getObject("business_id", Long.class),
                usageDescription(rs),
                toLocalDateTime(rs.getTimestamp("created_at"))
            ),
            tenantId,
            safePageSize,
            (safeCurrent - 1) * safePageSize
        );
        return new TeamPointTransactionPageResponse(records, total == null ? 0 : total, safeCurrent, safePageSize);
    }

    private String usageDescription(ResultSet rs) throws SQLException {
        String existing = rs.getString("description");
        if (existing != null && !existing.isBlank() && !"-".equals(existing.trim())) return existing;
        String entryType = rs.getString("entry_type");
        if ("GRANT".equals(entryType)) return "积分发放";

        StringJoiner description = new StringJoiner(" · ");
        String projectName = rs.getString("project_name");
        if (projectName != null && !projectName.isBlank()) description.add("《" + projectName + "》");
        description.add(usageScene(rs.getString("usage_scene"), rs.getString("usage_business_type")));
        Long executionId = rs.getObject("usage_execution_id", Long.class);
        Long businessId = rs.getObject("usage_business_id", Long.class);
        if (executionId != null) description.add("任务 #" + executionId);
        else if (businessId != null) description.add("业务任务 #" + businessId);
        description.add(switch (entryType) {
            case "RESERVE" -> "积分预扣，任务完成后按实际用量结算";
            case "INCREMENTAL_RESERVE" -> "追加预扣，供后续生成或模型调用使用";
            case "SETTLE" -> "实际消耗（从预扣积分结算）";
            case "RELEASE" -> "释放未使用的预扣积分，返还可用余额";
            case "REFUND" -> "退回已消耗的积分";
            case "SETTLEMENT_REVIEW" -> "用量待核对，尚未完成最终结算";
            default -> "积分变动";
        });
        return description.toString();
    }

    private String usageScene(String scene, String businessType) {
        for (AiBusinessScene value : AiBusinessScene.values()) {
            if (value.code().equals(scene)) return value.displayName();
        }
        if (scene != null) {
            String label = switch (scene) {
                case "script_analysis" -> "剧本分析";
                case "ai_image_generate" -> "图片生成";
                case "ai_video_generate" -> "视频生成";
                case "video_decomposition_video_analysis" -> "视频拆剧：视频理解";
                case "video_decomposition_draft_generation" -> "视频拆剧：剧本草稿生成";
                default -> null;
            };
            if (label != null) return label;
        }
        return switch (businessType == null ? "" : businessType) {
            case "AI_IMAGE", "AI_IMAGE_TASK" -> "图片生成";
            case "AI_VIDEO", "AI_VIDEO_TASK" -> "视频生成";
            case "SCRIPT_AI_OPERATION" -> "剧本AI处理";
            case "SCRIPT_ANALYSIS_TASK" -> "剧本分析";
            case "REVIEW_TASK" -> "剧本审核";
            case "VIDEO_DECOMPOSITION_EPISODE" -> "视频拆剧";
            default -> "AI任务";
        };
    }

    private TeamPointAccountResponse readAccount(Long tenantId) {
        return jdbcTemplate.queryForObject("""
            select tenant_id, balance, trial_balance, total_granted, total_consumed, updated_at
              from team_point_account
             where tenant_id = ?
            """,
            (rs, rowNum) -> new TeamPointAccountResponse(
                rs.getLong("tenant_id"),
                rs.getInt("balance"),
                rs.getBigDecimal("trial_balance"),
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

    private String responseType(String entryType, String idempotencyKey) {
        if (!"GRANT".equals(entryType)) return entryType;
        if (idempotencyKey != null
            && (idempotencyKey.startsWith("order:") || idempotencyKey.startsWith("subscription:"))) {
            return "ENTITLEMENT_GRANT";
        }
        return "ADJUST_GRANT";
    }

    private int responseAmount(String entryType, BigDecimal amount) {
        return ("RESERVE".equals(entryType)
            || "INCREMENTAL_RESERVE".equals(entryType)
            || "SETTLE".equals(entryType)
            ? amount.negate()
            : amount).intValue();
    }
}
