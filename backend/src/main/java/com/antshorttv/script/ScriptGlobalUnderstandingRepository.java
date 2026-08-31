package com.antshorttv.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ScriptGlobalUnderstandingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ScriptGlobalUnderstandingRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public long upsert(ScriptGlobalUnderstandingDocument document) {
        List<Long> existing = jdbc.queryForList("""
            select id from script_global_understanding
             where tenant_id = ? and script_id = ?
             for update
            """, Long.class, document.tenantId(), document.scriptId());
        String content = write(document.content());
        if (!existing.isEmpty()) {
            long id = existing.get(0);
            jdbc.update("""
                update script_global_understanding
                   set project_id = ?, schema_version = ?, content_json = ?,
                       analyzed_content_hash = ?, last_agent_run_id = ?, updated_by = ?, updated_at = now()
                 where id = ? and tenant_id = ?
                """, document.projectId(), document.schemaVersion(), content,
                document.analyzedContentHash(), document.lastAgentRunId(), document.updatedBy(), id,
                document.tenantId());
            return id;
        }
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into script_global_understanding
                  (tenant_id, project_id, script_id, schema_version, content_json,
                   analyzed_content_hash, last_agent_run_id, created_by, updated_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, document.tenantId());
            statement.setLong(2, document.projectId());
            statement.setLong(3, document.scriptId());
            statement.setInt(4, document.schemaVersion());
            statement.setString(5, content);
            statement.setString(6, document.analyzedContentHash());
            statement.setObject(7, document.lastAgentRunId());
            statement.setLong(8, document.createdBy());
            statement.setLong(9, document.updatedBy());
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("Global understanding id was not generated");
        }
        return id.longValue();
    }

    public Optional<ScriptGlobalUnderstandingDocument> findCurrent(Long tenantId, Long scriptId) {
        return jdbc.query("""
            select id, tenant_id, project_id, script_id, schema_version, content_json,
                   analyzed_content_hash, last_agent_run_id, created_by, updated_by, created_at, updated_at
              from script_global_understanding
             where tenant_id = ? and script_id = ?
            """, (row, index) -> new ScriptGlobalUnderstandingDocument(
            row.getLong("id"), row.getLong("tenant_id"), row.getLong("project_id"),
            row.getLong("script_id"), row.getInt("schema_version"), read(row.getString("content_json")),
            row.getString("analyzed_content_hash"), nullableLong(row, "last_agent_run_id"),
            row.getLong("created_by"), row.getLong("updated_by"),
            timestamp(row, "created_at"), timestamp(row, "updated_at")
        ), tenantId, scriptId).stream().findFirst();
    }

    public boolean stageSucceeded(Long tenantId, Long taskId, Long stageId) {
        Integer count = jdbc.queryForObject("""
            select count(*)
              from script_analysis_stage stage
              join script_analysis_task task on task.id = stage.task_id
             where task.tenant_id = ? and task.id = ? and stage.id = ?
               and stage.stage_code = 'GLOBAL_UNDERSTANDING' and stage.status = 'SUCCEEDED'
            """, Integer.class, tenantId, taskId, stageId);
        return count != null && count == 1;
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化剧情全局理解。", exception);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("剧情全局理解数据损坏。", exception);
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
