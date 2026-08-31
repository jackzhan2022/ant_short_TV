package com.antshorttv.workflowagent.run;

import com.antshorttv.ai.AiToolCall;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkflowAgentRunRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final long maxPayloadBytes;

    public WorkflowAgentRunRepository(
        JdbcTemplate jdbc,
        ObjectMapper json,
        WorkflowAgentProperties properties
    ) {
        this.jdbc = jdbc;
        this.json = json;
        this.maxPayloadBytes = properties.getMaxLogPayloadBytes();
    }

    @Transactional
    public Long start(WorkflowAgentRunStart start) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into ai_workflow_agent_run
                  (agent_id, agent_code, run_type, tenant_id, user_id, project_id, episode_id, script_id,
                   task_id, analysis_stage_id, status, model_id, temperature, max_tokens, max_steps, prompt_snapshot,
                   skill_snapshot_json, tool_codes_json, started_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, start.agentId());
            statement.setString(2, start.agentCode());
            statement.setString(3, start.runType());
            statement.setObject(4, start.tenantId());
            statement.setLong(5, start.userId());
            statement.setObject(6, start.projectId());
            statement.setObject(7, start.episodeId());
            statement.setObject(8, start.scriptId());
            statement.setObject(9, start.taskId());
            statement.setObject(10, start.analysisStageId());
            statement.setLong(11, start.modelId());
            statement.setBigDecimal(12, start.temperature());
            statement.setInt(13, start.maxTokens());
            statement.setInt(14, start.maxSteps());
            statement.setString(15, payload(start.promptSnapshot()));
            statement.setString(16, skillPayload(start.skillSnapshots()));
            statement.setString(17, payload(write(start.toolCodes())));
            return statement;
        }, keys);
        Number id = keys.getKey();
        return id == null ? null : id.longValue();
    }

    public void recordModelStep(
        Long runId,
        int stepNo,
        Long aiCallLogId,
        List<AiToolCall> toolCalls,
        String finalContent
    ) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("content", finalContent);
        snapshot.put("toolCalls", toolCalls == null ? List.of() : toolCalls);
        insertStep(runId, stepNo, "MODEL", "SUCCESS", aiCallLogId, null,
            null, payload(write(snapshot)), null, null);
    }

    public void recordFailedModelStep(
        Long runId,
        int stepNo,
        Long aiCallLogId,
        String errorCode,
        String errorMessage
    ) {
        insertStep(runId, stepNo, "MODEL", "FAILED", aiCallLogId, null,
            null, null, errorCode, payload(errorMessage));
    }

    public void recordToolStep(
        Long runId,
        int stepNo,
        String toolCode,
        String inputJson,
        String outputJson
    ) {
        insertStep(runId, stepNo, "TOOL", "SUCCESS", null, toolCode,
            payload(inputJson), payload(outputJson), null, null);
    }

    public void recordFailedToolStep(
        Long runId,
        int stepNo,
        String toolCode,
        String inputJson,
        String errorCode,
        String errorMessage
    ) {
        insertStep(runId, stepNo, "TOOL", "FAILED", null, toolCode,
            payload(inputJson), null, errorCode, payload(errorMessage));
    }

    public void complete(Long runId, String output) {
        jdbc.update("""
            update ai_workflow_agent_run
               set status = 'SUCCESS', final_output = ?, finished_at = now()
             where id = ? and status = 'RUNNING'
            """, payload(output), runId);
    }

    public void reconcileCommitted(Long runId, String output) {
        jdbc.update("""
            update ai_workflow_agent_run
               set status = 'SUCCESS', final_output = ?, error_code = null, error_message = null,
                   finished_at = coalesce(finished_at, now())
             where id = ?
            """, payload(output), runId);
    }

    public boolean belongsToStage(Long runId, Long tenantId, Long taskId, Long analysisStageId) {
        Integer count = jdbc.queryForObject("""
            select count(*) from ai_workflow_agent_run
             where id = ? and tenant_id = ? and task_id = ? and analysis_stage_id = ?
            """, Integer.class, runId, tenantId, taskId, analysisStageId);
        return count != null && count == 1;
    }

    public List<WorkflowAgentModelCall> modelCalls(Long runId, Long tenantId) {
        return jdbc.query("""
            select log.id, log.model_id, log.provider_id, log.provider_request_id,
                   log.transport_outcome, log.business_outcome
              from ai_workflow_agent_run_step step
              join ai_workflow_agent_run run on run.id = step.run_id
              join ai_call_log log on log.id = step.ai_call_log_id
             where step.run_id = ? and run.tenant_id = ? and step.step_type = 'MODEL'
             order by step.step_no
            """, (row, index) -> new WorkflowAgentModelCall(
            row.getLong("id"), nullableLong(row, "model_id"), nullableLong(row, "provider_id"),
            row.getString("provider_request_id"), row.getString("transport_outcome"),
            row.getString("business_outcome")
        ), runId, tenantId);
    }

    public void fail(Long runId, String errorCode, String errorMessage) {
        jdbc.update("""
            update ai_workflow_agent_run
               set status = 'FAILED', error_code = ?, error_message = ?, finished_at = now()
             where id = ? and status = 'RUNNING'
            """, errorCode, payload(errorMessage), runId);
    }

    public List<WorkflowAgentRunSummary> list(Long tenantId, String agentCode, int limit) {
        int boundedLimit = Math.max(1, Math.min(200, limit));
        String sql = """
            select id, agent_code, run_type, status, project_id, episode_id, final_output,
                   error_code, error_message, started_at, finished_at
              from ai_workflow_agent_run
            """ + (agentCode == null || agentCode.isBlank()
            ? " where tenant_id = ?" : " where tenant_id = ? and agent_code = ?")
            + " order by id desc limit " + boundedLimit;
        Object[] arguments = agentCode == null || agentCode.isBlank()
            ? new Object[]{tenantId} : new Object[]{tenantId, agentCode};
        return jdbc.query(sql, (row, index) -> new WorkflowAgentRunSummary(
            row.getLong("id"), row.getString("agent_code"), row.getString("run_type"),
            row.getString("status"), nullableLong(row, "project_id"), nullableLong(row, "episode_id"),
            row.getString("final_output"), row.getString("error_code"), row.getString("error_message"),
            timestamp(row, "started_at"), timestamp(row, "finished_at")
        ), arguments);
    }

    public WorkflowAgentRunDetail detail(Long tenantId, Long runId) {
        List<WorkflowAgentRunDetail> rows = jdbc.query("""
            select id, agent_id, agent_code, run_type, tenant_id, user_id, project_id, episode_id,
                   script_id, task_id, analysis_stage_id, status, model_id, temperature, max_tokens,
                   max_steps, prompt_snapshot,
                   skill_snapshot_json, tool_codes_json, final_output, error_code, error_message,
                   started_at, finished_at
              from ai_workflow_agent_run where tenant_id = ? and id = ?
            """, (row, index) -> new WorkflowAgentRunDetail(
            row.getLong("id"), nullableLong(row, "agent_id"), row.getString("agent_code"),
            row.getString("run_type"), nullableLong(row, "tenant_id"), row.getLong("user_id"),
            nullableLong(row, "project_id"), nullableLong(row, "episode_id"), nullableLong(row, "script_id"),
            nullableLong(row, "task_id"), nullableLong(row, "analysis_stage_id"),
            row.getString("status"), row.getLong("model_id"), row.getBigDecimal("temperature"),
            row.getInt("max_tokens"), row.getInt("max_steps"), row.getString("prompt_snapshot"),
            readSkills(row.getString("skill_snapshot_json")), readStrings(row.getString("tool_codes_json")),
            row.getString("final_output"), row.getString("error_code"), row.getString("error_message"),
            timestamp(row, "started_at"), timestamp(row, "finished_at"), List.of()
        ), tenantId, runId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 运行记录不存在。");
        }
        WorkflowAgentRunDetail run = rows.get(0);
        List<WorkflowAgentRunStepView> steps = jdbc.query("""
            select step.step_no, step.step_type, step.status, step.ai_call_log_id, step.tool_code,
                   step.input_json, step.output_json, step.error_code, step.error_message,
                   step.started_at, step.finished_at
              from ai_workflow_agent_run_step step
              join ai_workflow_agent_run run on run.id = step.run_id
             where step.run_id = ? and run.tenant_id = ? order by step.step_no
            """, (row, index) -> new WorkflowAgentRunStepView(
            row.getInt("step_no"), row.getString("step_type"), row.getString("status"),
            nullableLong(row, "ai_call_log_id"), row.getString("tool_code"), row.getString("input_json"),
            row.getString("output_json"), row.getString("error_code"), row.getString("error_message"),
            timestamp(row, "started_at"), timestamp(row, "finished_at")
        ), runId, tenantId);
        return new WorkflowAgentRunDetail(run.id(), run.agentId(), run.agentCode(), run.runType(),
            run.tenantId(), run.userId(), run.projectId(), run.episodeId(), run.scriptId(), run.taskId(),
            run.analysisStageId(), run.status(),
            run.modelId(), run.temperature(), run.maxTokens(), run.maxSteps(), run.promptSnapshot(),
            run.skillSnapshots(), run.toolCodes(), run.finalOutput(), run.errorCode(), run.errorMessage(),
            run.startedAt(), run.finishedAt(), steps);
    }

    private void insertStep(
        Long runId,
        int stepNo,
        String type,
        String status,
        Long callLogId,
        String toolCode,
        String input,
        String output,
        String errorCode,
        String errorMessage
    ) {
        jdbc.update("""
            insert into ai_workflow_agent_run_step
              (run_id, step_no, step_type, status, ai_call_log_id, tool_code, input_json,
               output_json, error_code, error_message, started_at, finished_at, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), now())
            """, runId, stepNo, type, status, callLogId, toolCode, input, output, errorCode, errorMessage);
    }

    private String payload(String value) {
        if (value == null) {
            return null;
        }
        String redacted = value
            .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
            .replaceAll("(?i)(api[_-]?key|access[_-]?key|secret|password)[\\\"']?\\s*[:=]\\s*[\\\"']?[^,;\\s\\\"'}]+", "$1=[REDACTED]")
            .replaceAll("(?i)sk-[A-Za-z0-9._-]+", "[REDACTED]");
        byte[] bytes = redacted.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxPayloadBytes) {
            return redacted;
        }
        int end = Math.min(redacted.length(), (int) maxPayloadBytes / 2);
        return redacted.substring(0, end) + "...[TRUNCATED]";
    }

    private String skillPayload(List<WorkflowAgentSkillSnapshot> snapshots) {
        List<WorkflowAgentSkillSnapshot> retained = new ArrayList<>();
        for (WorkflowAgentSkillSnapshot snapshot : snapshots) {
            String content = payload(snapshot.content());
            retained.add(new WorkflowAgentSkillSnapshot(
                snapshot.code(), snapshot.name(), snapshot.revision(), content));
            String candidate = write(retained);
            if (candidate.getBytes(StandardCharsets.UTF_8).length > maxPayloadBytes) {
                retained.remove(retained.size() - 1);
                retained.add(new WorkflowAgentSkillSnapshot(
                    "truncated", "Additional Skill snapshots omitted", "truncated", "[TRUNCATED]"));
                break;
            }
        }
        return write(retained);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化 Agent 运行日志。", exception);
        }
    }

    private List<WorkflowAgentSkillSnapshot> readSkills(String value) {
        return read(value, new TypeReference<>() {});
    }

    private List<String> readStrings(String value) {
        return read(value, new TypeReference<>() {});
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value == null ? "[]" : value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 运行快照损坏。", exception);
        }
    }

    private Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private LocalDateTime timestamp(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
