package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Narrow reads for the composer, history list and report reader. No version collections or AI logs. */
@Service
public class ReviewReadService {
    private final JdbcTemplate jdbc;
    private final TenantContextResolver tenants;
    private final RbacPermissionService permissions;
    private final ReviewAccessGuard access;
    private final ObjectMapper json;

    public ReviewReadService(JdbcTemplate jdbc, TenantContextResolver tenants, RbacPermissionService permissions,
        ReviewAccessGuard access, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.permissions = permissions;
        this.access = access;
        this.json = json;
    }

    private static final String VISIBLE = """
         from review_task t join review_project rp on rp.id=t.project_id and rp.tenant_id=t.tenant_id
         where t.tenant_id=? and rp.deleted_at is null and t.idempotency_key like 'direct:%'
           and (rp.main_project_id is null or exists (
             select 1 from project p where p.id=rp.main_project_id and p.tenant_id=rp.tenant_id and p.deleted_at is null))
           and (? = true or (rp.main_project_id is null and rp.created_by=?) or exists (
             select 1 from project_member pm
             join project_role pr on pr.id=pm.role_id and pr.project_id=pm.project_id
               and pr.tenant_id=pm.tenant_id and pr.status='ACTIVE'
             join project_role_permission prp on prp.role_id=pr.id and prp.project_id=pr.project_id and prp.tenant_id=pr.tenant_id
             join permission perm on perm.id=prp.permission_id and perm.code='MENU:PROJECTS'
             where pm.tenant_id=rp.tenant_id and pm.project_id=rp.main_project_id
               and pm.user_id=? and pm.status='ACTIVE'))
        """;

    public HistoryPage history(Long tenantId, int requestedPage, int requestedSize) {
        TenantContext context = requireAccess(tenantId);
        int page = Math.max(1, requestedPage);
        int size = Math.max(1, Math.min(100, requestedSize));
        boolean wide = permissions.canAccessAllProjects(context);
        Object[] args = {tenantId, wide, context.userId(), context.userId()};
        Long total = jdbc.queryForObject("select count(*) " + VISIBLE, Long.class, args);
        List<Summary> items = jdbc.query("""
            select t.id, t.project_id, rp.name, t.script_version_id, t.status, t.selected_dimensions_json,
                   t.created_at, t.completed_at
            """ + VISIBLE + " order by t.created_at desc, t.id desc limit ? offset ?",
            (rs, row) -> summary(rs), tenantId, wide, context.userId(), context.userId(), size, (long) (page - 1) * size);
        return new HistoryPage(items, page, size, total == null ? 0 : total);
    }

    public Progress progress(Long tenantId, Long taskId) {
        requireTaskAccess(tenantId, taskId);
        return jdbc.queryForObject("""
            select id, status, overall_progress, current_action, error_message, retry_kind
              from review_task where tenant_id=? and id=?
            """, (rs, row) -> new Progress(rs.getLong("id"), rs.getString("status"), rs.getInt("overall_progress"),
                rs.getString("current_action"), rs.getString("error_message"), rs.getString("retry_kind")), tenantId, taskId);
    }

    public Report report(Long tenantId, Long taskId) {
        requireTaskAccess(tenantId, taskId);
        return jdbc.queryForObject("""
            select t.id, t.project_id, rp.name, t.script_version_id, t.status, t.selected_dimensions_json,
                   t.created_at, t.completed_at, t.report_markdown
              from review_task t join review_project rp on rp.id=t.project_id and rp.tenant_id=t.tenant_id
             where t.tenant_id=? and t.id=?
            """, (rs, row) -> new Report(summary(rs), rs.getString("report_markdown")), tenantId, taskId);
    }

    public ReviewVersionResponse content(Long tenantId, Long taskId) {
        requireTaskAccess(tenantId, taskId);
        return jdbc.queryForObject("""
            select v.id, v.project_id, v.version_no, v.source_type, v.file_name, v.content, v.created_at
              from review_task t join review_script_version v on v.id=t.script_version_id and v.tenant_id=t.tenant_id
             where t.tenant_id=? and t.id=?
            """, (rs, row) -> new ReviewVersionResponse(rs.getLong("id"), rs.getLong("project_id"), rs.getInt("version_no"),
                rs.getString("source_type"), rs.getString("file_name"), rs.getString("content"), date(rs, "created_at")), tenantId, taskId);
    }

    private TenantContext requireAccess(Long tenantId) {
        return tenants.requireActiveMember(tenantId);
    }

    private void requireTaskAccess(Long tenantId, Long taskId) {
        TenantContext context = requireAccess(tenantId);
        List<ReviewProjectEntity> found = jdbc.query("""
            select rp.id, rp.tenant_id, rp.main_project_id, rp.created_by
              from review_task t join review_project rp on rp.id=t.project_id and rp.tenant_id=t.tenant_id
             where t.tenant_id=? and t.id=? and rp.deleted_at is null and t.idempotency_key like 'direct:%'
            """, (rs, row) -> {
                ReviewProjectEntity project = new ReviewProjectEntity();
                project.setId(rs.getLong("id"));
                project.setTenantId(rs.getLong("tenant_id"));
                project.setMainProjectId(rs.getObject("main_project_id", Long.class));
                project.setCreatedBy(rs.getLong("created_by"));
                return project;
            }, tenantId, taskId);
        if (found.isEmpty()) throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "审核记录不存在或无权访问。");
        access.require(context, found.get(0), "PROJECT:VIEW");
    }

    private Summary summary(ResultSet rs) throws SQLException {
        List<String> dimensions;
        try { dimensions = json.readValue(rs.getString("selected_dimensions_json"), new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new SQLException("Invalid stored review dimensions", e); }
        return new Summary(rs.getLong("id"), rs.getLong("project_id"), rs.getString("name"), rs.getLong("script_version_id"),
            rs.getString("status"), dimensions, date(rs, "created_at"), date(rs, "completed_at"));
    }

    private static LocalDateTime date(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    public record Summary(Long id, Long projectId, String name, Long scriptVersionId, String status,
        List<String> selectedDimensions, LocalDateTime createdAt, LocalDateTime completedAt) {}
    public record HistoryPage(List<Summary> items, int page, int pageSize, long total) {}
    public record Progress(Long id, String status, int overallProgress, String currentAction, String errorMessage, String retryKind) {}
    public record Report(Summary task, String reportMarkdown) {}
}
