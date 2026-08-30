package com.antshorttv.workflowagent.agent;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkflowAgentRepository {
    private final JdbcTemplate jdbc;

    public WorkflowAgentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkflowAgentRecord> list(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Long> ids = normalized.isEmpty()
            ? jdbc.queryForList("select id from ai_workflow_agent order by code", Long.class)
            : jdbc.queryForList("""
                select id from ai_workflow_agent
                 where lower(code) like ? or lower(name) like ? or lower(coalesce(description, '')) like ?
                 order by code
                """, Long.class, like(normalized), like(normalized), like(normalized));
        return ids.stream().map(this::getById).toList();
    }

    public WorkflowAgentRecord get(String code) {
        List<Long> ids = jdbc.queryForList(
            "select id from ai_workflow_agent where code = ?", Long.class, code);
        if (ids.isEmpty()) {
            throw notFound();
        }
        return getById(ids.get(0));
    }

    @Transactional
    public WorkflowAgentRecord create(WorkflowAgentCommand command, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                    insert into ai_workflow_agent
                      (code, name, description, system_prompt, model_id, temperature, max_tokens,
                       max_steps, status, revision, created_by, updated_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, now(), now())
                    """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, command.code());
                statement.setString(2, command.name());
                statement.setString(3, command.description());
                statement.setString(4, command.systemPrompt());
                statement.setLong(5, command.modelId());
                statement.setBigDecimal(6, command.temperature());
                statement.setInt(7, command.maxTokens());
                statement.setInt(8, command.maxSteps());
                statement.setString(9, command.status());
                statement.setObject(10, userId);
                statement.setObject(11, userId);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_CONFLICT, "Agent code 已存在。");
        }
        Long id = keyHolder.getKey().longValue();
        replaceAssociations(id, command.skillCodes(), command.toolCodes());
        return getById(id);
    }

    @Transactional
    public WorkflowAgentRecord update(
        String code,
        Long expectedRevision,
        WorkflowAgentCommand command,
        Long userId
    ) {
        WorkflowAgentRecord current = get(code);
        int changed = jdbc.update("""
            update ai_workflow_agent
               set name = ?, description = ?, system_prompt = ?, model_id = ?, temperature = ?,
                   max_tokens = ?, max_steps = ?, status = ?, revision = revision + 1,
                   updated_by = ?, updated_at = now()
             where id = ? and revision = ?
            """, command.name(), command.description(), command.systemPrompt(), command.modelId(),
            command.temperature(), command.maxTokens(), command.maxSteps(), command.status(), userId,
            current.id(), expectedRevision);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_CONFLICT,
                "Agent 已被其他人修改，请重新加载后再保存。");
        }
        replaceAssociations(current.id(), command.skillCodes(), command.toolCodes());
        return getById(current.id());
    }

    @Transactional
    public WorkflowAgentRecord copy(String sourceCode, String targetCode, Long userId) {
        WorkflowAgentRecord source = get(sourceCode);
        return create(new WorkflowAgentCommand(targetCode, source.name(), source.description(),
            source.systemPrompt(), source.modelId(), source.temperature(), source.maxTokens(),
            source.maxSteps(), source.status(), source.skillCodes(), source.toolCodes()), userId);
    }

    @Transactional
    public WorkflowAgentRecord setStatus(String code, String status, Long userId) {
        WorkflowAgentRecord current = get(code);
        jdbc.update("""
            update ai_workflow_agent
               set status = ?, revision = revision + 1, updated_by = ?, updated_at = now()
             where id = ?
            """, status, userId, current.id());
        return getById(current.id());
    }

    @Transactional
    public void delete(String code) {
        WorkflowAgentRecord current = get(code);
        jdbc.update("delete from ai_workflow_agent_tool where agent_id = ?", current.id());
        jdbc.update("delete from ai_workflow_agent_skill where agent_id = ?", current.id());
        jdbc.update("delete from ai_workflow_agent where id = ?", current.id());
    }

    private WorkflowAgentRecord getById(Long id) {
        WorkflowAgentRecord scalar = jdbc.queryForObject("""
            select id, code, name, description, system_prompt, model_id, temperature,
                   max_tokens, max_steps, status, revision, created_by, updated_by, created_at, updated_at
              from ai_workflow_agent where id = ?
            """, (result, row) -> new WorkflowAgentRecord(
                result.getLong("id"), result.getString("code"), result.getString("name"),
                result.getString("description"), result.getString("system_prompt"),
                result.getLong("model_id"), result.getBigDecimal("temperature"),
                result.getInt("max_tokens"), result.getInt("max_steps"), result.getString("status"),
                result.getLong("revision"), nullableLong(result, "created_by"),
                nullableLong(result, "updated_by"),
                result.getTimestamp("created_at").toLocalDateTime(),
                result.getTimestamp("updated_at").toLocalDateTime(), List.of(), List.of()), id);
        List<String> skills = jdbc.queryForList("""
            select skill_code from ai_workflow_agent_skill where agent_id = ? order by load_order
            """, String.class, id);
        List<String> tools = jdbc.queryForList("""
            select tool_code from ai_workflow_agent_tool where agent_id = ? order by tool_code
            """, String.class, id);
        return new WorkflowAgentRecord(scalar.id(), scalar.code(), scalar.name(), scalar.description(),
            scalar.systemPrompt(), scalar.modelId(), scalar.temperature(), scalar.maxTokens(),
            scalar.maxSteps(), scalar.status(), scalar.revision(), scalar.createdBy(), scalar.updatedBy(),
            scalar.createdAt(), scalar.updatedAt(), skills, tools);
    }

    private void replaceAssociations(Long agentId, List<String> skillCodes, List<String> toolCodes) {
        jdbc.update("delete from ai_workflow_agent_skill where agent_id = ?", agentId);
        jdbc.update("delete from ai_workflow_agent_tool where agent_id = ?", agentId);
        for (int index = 0; index < skillCodes.size(); index++) {
            jdbc.update("""
                insert into ai_workflow_agent_skill (agent_id, skill_code, load_order, created_at)
                values (?, ?, ?, now())
                """, agentId, skillCodes.get(index), index);
        }
        for (String toolCode : toolCodes) {
            jdbc.update("""
                insert into ai_workflow_agent_tool (agent_id, tool_code, created_at) values (?, ?, now())
                """, agentId, toolCode);
        }
    }

    private Long nullableLong(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private String like(String value) {
        return "%" + value + "%";
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.WORKFLOW_AGENT_NOT_FOUND, "Agent 不存在。");
    }
}
