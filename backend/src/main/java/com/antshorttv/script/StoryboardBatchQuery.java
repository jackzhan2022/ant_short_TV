package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Narrow, bounded queries for batch progress; generation payloads never leave MySQL. */
final class StoryboardBatchQuery {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private volatile Boolean h2;

    StoryboardBatchQuery(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    List<StoryboardBatchItemResponse> selectItems(StoryboardBatchEntity batch) {
        List<ExecutionItem> items = jdbc.query("""
            select item.id, item.episode_id, item.episode_no, item.execution_id,
                   execution.id as found_execution_id, execution.status,
                   execution.business_call_count, execution.technical_retry_count,
                   execution.settled_points, execution.error_code, execution.error_message
              from storyboard_batch_item item
              left join ai_execution_task execution on execution.id = item.execution_id
             where item.tenant_id = ? and item.project_id = ? and item.batch_id = ?
             order by item.episode_no, item.id
            """, (row, index) -> {
                long executionId = row.getLong("execution_id");
                if (row.getObject("found_execution_id") == null) {
                    throw new IllegalStateException("Storyboard batch execution is missing: " + executionId);
                }
                return new ExecutionItem(row.getLong("id"), row.getLong("episode_id"),
                    row.getInt("episode_no"), executionId, row.getString("status"),
                    row.getInt("business_call_count"), row.getInt("technical_retry_count"),
                    row.getBigDecimal("settled_points"), row.getString("error_code"), row.getString("error_message"));
            }, batch.tenantId, batch.projectId, batch.id);
        Map<Long, Integer> warnings = items.stream().anyMatch(item -> "SUCCEEDED".equals(item.status()))
            ? warningCounts(batch) : Map.of();
        return items.stream().map(item -> {
            int warningCount = "SUCCEEDED".equals(item.status()) ? warnings.getOrDefault(item.id(), 0) : 0;
            return new StoryboardBatchItemResponse(item.id(), item.episodeId(), item.episodeNo(),
                item.executionId(), StoryboardBatchService.itemStatus(item.status(), warningCount),
                warningCount, item.businessCallCount(), item.technicalRetryCount(),
                item.settledPoints() == null ? BigDecimal.ZERO : item.settledPoints(),
                item.errorCode(), item.errorMessage());
        }).toList();
    }

    private Map<Long, Integer> warningCounts(StoryboardBatchEntity batch) {
        // H2 has no MySQL JSON_EXTRACT/JSON_LENGTH equivalents. Only tests use this fallback.
        if (h2 == null) {
            h2 = jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "H2".equals(connection.getMetaData().getDatabaseProductName()));
        }
        String projection = Boolean.TRUE.equals(h2)
            ? "item.id, storyboard.shot_plan_json"
            : "item.id, sum(" + arrayLength("$.warnings") + " + "
                + arrayLength("$.diagnostics.classificationWarnings") + ") as warning_count";
        String sql = "select " + projection + """
              from storyboard_batch_item item
              join ai_execution_task execution on execution.id = item.execution_id and execution.status = 'SUCCEEDED'
              join storyboard on storyboard.tenant_id = item.tenant_id
                   and storyboard.project_id = item.project_id and storyboard.episode_id = item.episode_id
                   and storyboard.deleted_at is null
             where item.tenant_id = ? and item.project_id = ? and item.batch_id = ?
               and exists (
                   select 1 from ai_workflow_agent_run_step step
                   join ai_call_log call_log on call_log.id = step.ai_call_log_id
                   where call_log.execution_id = item.execution_id
                     and step.run_id = storyboard.generated_by_run_id
               )
            """ + (Boolean.TRUE.equals(h2) ? "" : " group by item.id");
        Map<Long, Integer> counts = new HashMap<>();
        jdbc.query(sql, (RowCallbackHandler) row -> {
            int count = Boolean.TRUE.equals(h2)
                ? historicalWarningCount(row.getString("shot_plan_json")) : row.getInt("warning_count");
            counts.merge(row.getLong("id"), count, Integer::sum);
        }, batch.tenantId, batch.projectId, batch.id);
        return counts;
    }

    private static String arrayLength(String path) {
        // Sanitize before any JSON function: malformed historical text contributes zero.
        String document = "if(json_valid(storyboard.shot_plan_json), storyboard.shot_plan_json, '{}')";
        return "case when json_type(json_extract(" + document + ", '" + path + "')) = 'ARRAY'"
            + " then json_length(" + document + ", '" + path + "') else 0 end";
    }

    private int historicalWarningCount(String plan) {
        try {
            JsonNode parsed = json.readTree(plan);
            JsonNode warnings = parsed.path("warnings");
            JsonNode classification = parsed.path("diagnostics").path("classificationWarnings");
            return (warnings.isArray() ? warnings.size() : 0)
                + (classification.isArray() ? classification.size() : 0);
        } catch (Exception ignored) {
            // A malformed historical plan is not a generated quality warning.
            return 0;
        }
    }

    private record ExecutionItem(Long id, Long episodeId, Integer episodeNo, Long executionId,
        String status, int businessCallCount, int technicalRetryCount, BigDecimal settledPoints,
        String errorCode, String errorMessage) {
    }
}
