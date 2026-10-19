package com.antshorttv.points;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Project-wide subsidy for script analysis and storyboarding; model tariffs stay unchanged. */
@Service
public class WorkbenchPointPolicy {
    public static final BigDecimal RATE = new BigDecimal("0.20");
    public static final BigDecimal PROJECT_LIMIT = new BigDecimal("1000");
    private static final String SCENES = "'script_analysis','script_episode_split','script_episode_summary',"
        + "'script_character_scene_recognition','scoped_asset_reextraction','storyboard_breakdown'";
    private static final Set<String> AGENTS = Set.of("short-drama-global-understanding",
        "short-drama-episode-splitting", "short-drama-episode-summary",
        "short-drama-asset-recognition", "short-drama-storyboard");
    private final JdbcTemplate jdbc;

    public WorkbenchPointPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static boolean includesAgent(String code) { return code != null && AGENTS.contains(code); }

    public static BigDecimal discounted(BigDecimal points) {
        return points.multiply(RATE).setScale(8, RoundingMode.HALF_UP);
    }

    public Long project(Long tenantId, Long executionId) {
        List<Long> rows = jdbc.queryForList("select project_id from ai_execution_task where tenant_id=? and id=? "
            + "and project_id is not null and capability='TEXT' and scene in (" + SCENES + ")",
            Long.class, tenantId, executionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public BigDecimal reserve(Long tenantId, Long executionId, BigDecimal points) {
        Long projectId = project(tenantId, executionId);
        return projectId == null ? points : discounted(points).min(remaining(tenantId, projectId, false, true));
    }

    public BigDecimal reserve(AiPointReservationEntity reservation, BigDecimal points) {
        Long projectId = project(reservation.tenantId, reservation.executionId);
        if (projectId == null) return points;
        initializeBaseline(reservation);
        return discounted(points).min(remaining(reservation.tenantId, projectId, false, true));
    }

    public BigDecimal settlement(AiPointReservationEntity reservation, BigDecimal points, boolean cumulative) {
        Long projectId = project(reservation.tenantId, reservation.executionId);
        if (projectId == null) return cumulative ? points.subtract(reservation.settledPoints).max(BigDecimal.ZERO) : points;
        initializeBaseline(reservation);
        points = assessedTarget(reservation, points, cumulative);
        if (cumulative) points = points.subtract(reservation.workbenchAssessedPoints).max(BigDecimal.ZERO);
        // Pending budgets are not charges. They must not turn another completed call free
        // before the project's actual charges have reached the ceiling.
        return points.min(remaining(reservation.tenantId, projectId, true, true));
    }

    public void recordSettlement(AiPointReservationEntity reservation, BigDecimal points, boolean cumulative) {
        if (project(reservation.tenantId, reservation.executionId) == null) return;
        initializeBaseline(reservation);
        BigDecimal assessed = assessedTarget(reservation, points, cumulative);
        reservation.workbenchAssessedPoints = cumulative
            ? reservation.workbenchAssessedPoints.max(assessed) : reservation.workbenchAssessedPoints.add(assessed);
    }

    private BigDecimal assessedTarget(AiPointReservationEntity reservation, BigDecimal points, boolean cumulative) {
        return cumulative
            ? reservation.workbenchBaselinePoints.add(discounted(points.subtract(reservation.workbenchBaselinePoints).max(BigDecimal.ZERO)))
            : discounted(points);
    }

    public List<Long> sharedBudgetReservations(AiPointReservationEntity reservation) {
        Long projectId = project(reservation.tenantId, reservation.executionId);
        if (projectId == null) return List.of();
        return jdbc.queryForList("select id from ai_point_reservation where tenant_id=? and workbench_project_id=? "
            + "and id<>? and reserved_points>settled_points+released_points order by id for update",
            Long.class, reservation.tenantId, projectId, reservation.id);
    }

    private void initializeBaseline(AiPointReservationEntity reservation) {
        // Old settled calls keep their original charge when a failed execution is resumed.
        if (reservation.workbenchBaselinePoints == null) reservation.workbenchBaselinePoints = reservation.settledPoints;
        if (reservation.workbenchAssessedPoints == null) reservation.workbenchAssessedPoints = reservation.settledPoints;
    }

    public BigDecimal estimateRemaining(Long tenantId, Long projectId) {
        return projectId == null ? PROJECT_LIMIT : remaining(tenantId, projectId, true, false);
    }

    private BigDecimal remaining(Long tenantId, Long projectId, boolean consumedOnly, boolean lock) {
        // The caller holds the account lock before locking reservation rows. A current read is
        // essential here: a repeatable-read snapshot could miss a concurrently committed charge.
        String amount = consumedOnly ? "settled_points-refunded_points" : "reserved_points-released_points-refunded_points";
        String sql = "select " + amount + " as committed_points "
            + "from ai_point_reservation where tenant_id=? and workbench_project_id=?"
            + (lock ? " for update" : "");
        BigDecimal committed = BigDecimal.ZERO;
        for (var row : jdbc.queryForList(sql, tenantId, projectId)) {
            committed = committed.add((BigDecimal) row.get("committed_points"));
        }
        return PROJECT_LIMIT.subtract(committed).max(BigDecimal.ZERO);
    }
}
