package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** DML only: account changes, audit entries and retirement commit together before V115 DDL. */
public class V114__retire_legacy_workflow_data extends BaseJavaMigration {
    private static final String REASON = "LEGACY_WORKFLOW_RETIRED";
    private static final String OLD_SCENES =
        "'script_element_extract','character_extract','scene_extract','prop_extract'";

    @Override
    public void migrate(Context context) throws Exception {
        cleanup(context.getConnection());
    }

    public static void cleanup(Connection connection) throws Exception {
        boolean ownsTransaction = connection.getAutoCommit();
        if (ownsTransaction) connection.setAutoCommit(false);
        Savepoint savepoint = ownsTransaction ? null : connection.setSavepoint();
        try {
            retire(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
            if (ownsTransaction) connection.commit();
        } catch (Exception failure) {
            if (ownsTransaction) connection.rollback();
            else connection.rollback(savepoint);
            throw failure;
        } finally {
            if (ownsTransaction) connection.setAutoCommit(true);
            else connection.releaseSavepoint(savepoint);
        }
    }

    private static void retire(JdbcTemplate jdbc) throws Exception {
        List<Map<String, Object>> analyses = jdbc.queryForList("""
            select task.id, task.tenant_id, task.execution_id
              from script_analysis_task task
             where task.pipeline_version='LEGACY_V1'
               and not exists (
                   select 1 from script_analysis_stage stage
                   join ai_workflow_agent_run run on run.analysis_stage_id=stage.id
                   where stage.task_id=task.id)
               and not exists (
                   select 1 from ai_workflow_agent_run run
                   where run.task_id=task.id and run.tenant_id=task.tenant_id
                     and run.project_id=task.project_id
                     and run.agent_code in ('short-drama-global-understanding',
                         'short-drama-episode-splitting','short-drama-episode-summary',
                         'short-drama-asset-recognition'))
               and not exists (
                   select 1 from script_analysis_fanout_snapshot snapshot
                   where snapshot.task_id=task.id)
             order by task.id
            """);
        List<Map<String, Object>> reviews = jdbc.queryForList("""
            select id, tenant_id, project_id, execution_id from review_task
             where coalesce(result_format,'')<>'MARKDOWN' order by id
            """);
        List<Map<String, Object>> operations = jdbc.queryForList("""
            select id, tenant_id, execution_id from script_ai_operation
             where operation_type='ELEMENT_EXTRACT' order by id
            """);
        Set<Long> executionIds = new LinkedHashSet<>(jdbc.queryForList(
            "select id from ai_execution_task where scene in (" + OLD_SCENES + ") order by id", Long.class));
        collectExecutions(jdbc, executionIds, analyses, "SCRIPT_ANALYSIS_TASK");
        collectExecutions(jdbc, executionIds, reviews, "REVIEW_TASK");
        collectExecutions(jdbc, executionIds, operations, "SCRIPT_AI_OPERATION");
        for (Long executionId : executionIds.stream().sorted().toList()) {
            retireExecution(jdbc, executionId);
        }
        for (Map<String, Object> row : reviews) {
            long id = number(row, "id");
            retireReviewRuns(jdbc, row);
            jdbc.update("update review_project set last_task_id=null where last_task_id=? and tenant_id=?", id, number(row, "tenant_id"));
            jdbc.update("delete from review_export_record where task_id=? and tenant_id=?", id, number(row, "tenant_id"));
            // Cascades remove the retired snapshot's units, fragments, candidate and semantic records.
            jdbc.update("delete from review_fanout_snapshot where task_id=?", id);
            jdbc.update("delete from review_task where id=?", id);
        }
        for (Map<String, Object> row : analyses) {
            long id = number(row, "id");
            jdbc.update("delete from script_analysis_config_snapshot where task_id=?", id);
            jdbc.update("delete from script_analysis_result where task_id=?", id);
            jdbc.update("delete from script_analysis_stage where task_id=?", id);
            jdbc.update("delete from script_analysis_task where id=?", id);
        }
        for (Map<String, Object> row : operations) {
            jdbc.update("""
                update script_ai_operation set status='CANCELED',error_code=?,
                    error_message='Legacy workflow retired by migration V114',
                    completed_at=coalesce(completed_at,now()),updated_at=now()
                 where id=? and status in ('PENDING','RUNNING')
                """, REASON, number(row, "id"));
        }
        retainModelSnapshots(jdbc);
        jdbc.update("""
            delete from ai_workflow_agent_tool
             where tool_code in ('save_review_unit_result','read_review_unit_results',
                   'read_review_candidates','save_review_semantic_decisions','save_review_result')
            """);
        jdbc.update("""
            delete from ai_workflow_agent_skill
             where skill_code='script-review-semantic-quality'
            """);
        jdbc.update("""
            delete from platform_role_permission where permission_id in
              (select id from platform_permission where code in ('PLATFORM_AI_AGENT_VIEW','PLATFORM_AI_AGENT_EDIT','PLATFORM_AI_SKILL_EDIT'))
            """);
        jdbc.update("delete from platform_permission where code in ('PLATFORM_AI_AGENT_VIEW','PLATFORM_AI_AGENT_EDIT','PLATFORM_AI_SKILL_EDIT')");
    }

    private static void retireReviewRuns(JdbcTemplate jdbc, Map<String, Object> task) {
        // task_id is shared across domains: require tenant/project plus review identity or an explicit link.
        List<Long> runIds = jdbc.queryForList("""
            select run.id from ai_workflow_agent_run run
             where run.task_id=? and run.tenant_id=? and run.project_id=?
               and ((run.run_type='FORMAL' and run.agent_code='script-review')
                 or exists (select 1 from review_task task
                     where task.id=run.task_id and task.tenant_id=run.tenant_id and task.project_id=run.project_id
                       and (task.workflow_agent_run_id=run.id or task.aggregation_run_id=run.id))
                 or exists (select 1 from review_fanout_snapshot snapshot
                     where snapshot.task_id=run.task_id and snapshot.tenant_id=run.tenant_id
                       and snapshot.project_id=run.project_id
                       and (snapshot.aggregation_run_id=run.id
                         or exists (select 1 from review_fanout_unit unit
                             where unit.snapshot_id=snapshot.id and unit.child_run_id=run.id)
                         or exists (select 1 from review_unit_result result
                             where result.snapshot_id=snapshot.id and result.child_run_id=run.id))))
             order by run.id for update
            """, Long.class, number(task, "id"), number(task, "tenant_id"), number(task, "project_id"));
        for (Long runId : runIds) {
            jdbc.update("""
                update ai_workflow_agent_run_step set status='CANCELED',finished_at=coalesce(finished_at,now()),
                    error_code=?,error_message='Legacy workflow retired by migration V114'
                 where run_id=? and status in ('PENDING','RUNNING','STARTED')
                """, REASON, runId);
            jdbc.update("""
                update ai_workflow_agent_run set status='CANCELED',finished_at=coalesce(finished_at,now()),
                    error_code=?,error_message='Legacy workflow retired by migration V114'
                 where id=? and status in ('PENDING','RUNNING','STARTED')
                """, REASON, runId);
        }
    }

    private static void collectExecutions(JdbcTemplate jdbc, Set<Long> ids,
        List<Map<String, Object>> tasks, String businessType) {
        for (Map<String, Object> task : tasks) {
            // Include every regeneration version, with the exact tenant/domain boundary.
            ids.addAll(jdbc.queryForList("""
                select id from ai_execution_task
                 where tenant_id=? and business_type=? and business_id=?
                """, Long.class, number(task, "tenant_id"), businessType, number(task, "id")));
        }
    }

    private static void retireExecution(JdbcTemplate jdbc, long executionId) {
        Map<String, Object> execution = jdbc.queryForMap("select * from ai_execution_task where id=? for update", executionId);
        jdbc.update("""
            update ai_execution_task
               set status='CANCELED', phase='CANCELED', retryable=false, next_run_at=null,
                   claim_token=null, claimed_at=null, heartbeat_at=null, claim_expires_at=null,
                   error_code=?, error_message='Legacy workflow retired by migration V114',
                   canceled_at=now(), completed_at=coalesce(completed_at,now()), updated_at=now()
             where id=? and status in ('PENDING','RUNNING')
            """, REASON, executionId);
        jdbc.update("""
            update ai_execution_attempt
               set status='CANCELED', retryable=false, next_retry_at=null, finished_at=now(),
                   error_code=?, error_message='Legacy workflow retired by migration V114'
             where execution_id=? and status='STARTED'
            """, REASON, executionId);
        jdbc.update("""
            update ai_execution_task set retryable=false,next_run_at=null,updated_at=now()
             where id=? and (retryable=true or next_run_at is not null)
            """, executionId);
        jdbc.update("""
            update ai_execution_attempt set retryable=false,next_retry_at=null
             where execution_id=? and (retryable=true or next_retry_at is not null)
            """, executionId);
        List<Map<String, Object>> reservations = jdbc.queryForList("""
            select * from ai_point_reservation where execution_id=? and tenant_id=?
             and status in ('RESERVED','SETTLEMENT_REVIEW_REQUIRED') order by id for update
            """, executionId, number(execution, "tenant_id"));
        for (Map<String, Object> reservation : reservations) {
            releaseReservation(jdbc, reservation);
        }
        // Keep completed reservations/executions byte-for-byte unchanged when no release was necessary.
        if (!reservations.isEmpty()) {
            List<Map<String, Object>> current = jdbc.queryForList("""
                select status,reserved_points,settled_points,released_points from ai_point_reservation
                 where execution_id=? and execution_version=? and tenant_id=?
                """, executionId, execution.get("execution_version"), number(execution, "tenant_id"));
            if (!current.isEmpty()) {
                Map<String, Object> reservation = current.get(0);
                jdbc.update("""
                    update ai_execution_task set point_settlement_status=?,reserved_points=?,
                        settled_points=?,released_points=?,updated_at=now() where id=?
                    """, reservation.get("status"), reservation.get("reserved_points"),
                    reservation.get("settled_points"), reservation.get("released_points"), executionId);
            }
        }
    }

    private static void releaseReservation(JdbcTemplate jdbc, Map<String, Object> reservation) {
        long id = number(reservation, "id");
        long tenantId = number(reservation, "tenant_id");
        BigDecimal remaining = decimal(reservation, "reserved_points")
            .subtract(decimal(reservation, "settled_points"))
            .subtract(decimal(reservation, "released_points"));
        if (remaining.signum() < 0) throw new IllegalStateException("Negative reservation remainder: " + id);
        String key = "migration:V114:reservation:" + id + ":release";
        if (jdbc.queryForObject("select count(*) from point_ledger where tenant_id=? and idempotency_key=?",
            Integer.class, tenantId, key) != 0) {
            throw new IllegalStateException("Release audit exists for an unsettled reservation: " + id);
        }
        if (remaining.signum() > 0) {
            jdbc.queryForMap("select balance,reserved_balance from team_point_account where tenant_id=? for update", tenantId);
            int updated = jdbc.update("""
                update team_point_account set balance=balance+?,reserved_balance=reserved_balance-?,
                    total_released=total_released+?,version=version+1,updated_at=now()
                 where tenant_id=? and reserved_balance>=?
                """, remaining, remaining, remaining, tenantId, remaining);
            if (updated != 1) throw new IllegalStateException("Account cannot cover reservation: " + id);
        }
        jdbc.update("""
            update ai_point_reservation set status='RELEASED',released_points=released_points+?,
                released_at=now(),updated_at=now() where id=?
            """, remaining, id);
        if (remaining.signum() > 0) {
            Map<String, Object> account = jdbc.queryForMap(
                "select balance,reserved_balance from team_point_account where tenant_id=?", tenantId);
            List<Map<String, Object>> attempts = jdbc.queryForList("""
                select id,ai_call_log_id from ai_execution_attempt
                 where execution_id=? and execution_version=? order by id desc limit 1
                """, reservation.get("execution_id"), reservation.get("execution_version"));
            Map<String, Object> attempt = attempts.isEmpty() ? Map.of() : attempts.get(0);
            jdbc.update("""
                insert into point_ledger
                  (tenant_id,user_id,execution_id,execution_version,business_type,business_id,
                   reservation_id,policy_version_id,attempt_id,ai_call_log_id,entry_type,amount,available_balance_after,
                   reserved_balance_after,idempotency_key,description,created_at)
                values (?,?,?,?,?,?,?,?,?,?,'RELEASE',?,?,?,?,?,now())
                """, tenantId, reservation.get("user_id"), reservation.get("execution_id"),
                reservation.get("execution_version"), reservation.get("business_type"), reservation.get("business_id"),
                id, reservation.get("policy_version_id"), attempt.get("id"), attempt.get("ai_call_log_id"),
                remaining, account.get("balance"),
                account.get("reserved_balance"), key, "V114 legacy workflow retirement; released only unspent reservation");
        }
    }

    private static void retainModelSnapshots(JdbcTemplate jdbc) throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (Map<String, Object> row : jdbc.queryForList("select id,snapshot_json from script_analysis_config_snapshot order by id")) {
            var payload = json.readTree((String) row.get("snapshot_json"));
            var model = payload == null ? null : payload.get("modelId");
            if (model == null || !model.isIntegralNumber() || !model.canConvertToLong() || model.asLong() <= 0) {
                throw new IllegalStateException("Current analysis snapshot has no valid frozen modelId: " + row.get("id"));
            }
            jdbc.update("update script_analysis_config_snapshot set snapshot_json=? where id=?",
                json.writeValueAsString(Map.of("modelId", model.asLong())), row.get("id"));
        }
    }

    private static long number(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).longValue();
    }

    private static BigDecimal decimal(Map<String, Object> row, String field) {
        return (BigDecimal) row.get(field);
    }
}
