package com.antshorttv.workflowagent.skill;

import com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantWorkflowSkillRepository {
    private final JdbcTemplate jdbc;

    public TenantWorkflowSkillRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<WorkflowSkillView> find(Long tenantId, String code) {
        return select(" and skill.tenant_id=? and skill.skill_code=?", tenantId, code).stream().findFirst();
    }

    public List<WorkflowSkillView> listAll() { return select(""); }

    public List<WorkflowSkillView> listForTenant(Long tenantId) {
        return select(" and skill.tenant_id=?", tenantId);
    }

    public boolean hasOverrides(String code) {
        return jdbc.queryForObject("select count(*) from tenant_workflow_skill where skill_code=?", Integer.class, code) > 0;
    }

    private List<WorkflowSkillView> select(String condition, Object... args) {
        return jdbc.query("""
            select skill.*, tenant.name as tenant_name from tenant_workflow_skill skill
              join tenant on tenant.id=skill.tenant_id and tenant.deleted_at is null
             where 1=1
            """ + condition + " order by skill.tenant_id, skill.skill_code", (rs, row) ->
                new WorkflowSkillView(rs.getString("skill_code"), rs.getString("name"),
                    rs.getString("description"), rs.getString("content"), rs.getString("revision"),
                    List.of(StoryboardAgentBootstrap.AGENT_CODE), "TENANT", rs.getLong("tenant_id"),
                    rs.getString("tenant_name"), true), args);
    }

    public void insert(Long tenantId, SkillDocument document, String revision, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            insert into tenant_workflow_skill
              (tenant_id, skill_code, name, description, content, revision,
               created_by, updated_by, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)
            """, tenantId, document.code(), document.name(), document.description(), document.content(),
            revision, userId, userId, now, now);
    }

    public boolean update(Long tenantId, SkillDocument document, String revision,
        String expectedRevision, Long userId) {
        return jdbc.update("""
            update tenant_workflow_skill set name=?, description=?, content=?, revision=?, updated_by=?, updated_at=?
             where tenant_id=? and skill_code=? and revision=?
            """, document.name(), document.description(), document.content(), revision, userId,
            LocalDateTime.now(), tenantId, document.code(), expectedRevision) == 1;
    }

    public boolean delete(Long tenantId, String code, String expectedRevision) {
        return jdbc.update("delete from tenant_workflow_skill where tenant_id=? and skill_code=? and revision=?",
            tenantId, code, expectedRevision) == 1;
    }
}
