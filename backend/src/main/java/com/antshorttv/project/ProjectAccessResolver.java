package com.antshorttv.project;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.antshorttv.common.MediaPage;
import com.antshorttv.common.PageBounds;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessResolver {

    private final TenantContextResolver tenantContextResolver;
    private final RbacPermissionService permissionService;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectRoleMapper roleMapper;

    public ProjectAccessResolver(
        TenantContextResolver tenantContextResolver,
        RbacPermissionService permissionService,
        ProjectMapper projectMapper,
        ProjectMemberMapper memberMapper,
        ProjectRoleMapper roleMapper
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.permissionService = permissionService;
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.roleMapper = roleMapper;
    }

    public ProjectAccessContext requireView(Long tenantId, Long projectId) {
        TenantContext tenant = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = projectMapper.selectByTenantIdAndId(tenantId, projectId);
        if (project == null) {
            throw denied();
        }

        Set<String> tenantPermissions = permissionService.permissionCodes(tenant);
        return requireView(tenant, project, tenantPermissions);
    }

    private ProjectAccessContext requireView(
        TenantContext tenant, ProjectEntity project, Set<String> tenantPermissions
    ) {
        Long tenantId = tenant.tenantId();
        Long projectId = project.id;
        if (tenantPermissions.contains("PROJECT:VIEW_ALL")) {
            return context(tenant, project, ProjectAccessSource.TENANT_WIDE, null, null, tenantPermissions);
        }

        ProjectMemberEntity member = memberMapper.selectActiveByProjectIdAndUserId(
            tenantId,
            projectId,
            tenant.userId()
        );
        if (member == null || member.roleId == null) {
            throw denied();
        }
        ProjectRoleEntity role = roleMapper.selectByTenantProjectAndId(tenantId, projectId, member.roleId);
        if (role == null || !ProjectRoleStatus.ACTIVE.name().equals(role.status)) {
            throw denied();
        }
        Set<String> projectPermissions = permissionService.projectRolePermissionCodes(tenantId, projectId, member.roleId);
        if (!projectPermissions.contains("PROJECT:VIEW")) {
            throw denied();
        }
        return context(tenant, project, ProjectAccessSource.PROJECT_MEMBER, member, role, projectPermissions);
    }

    public List<ProjectEntity> accessibleProjects(Long tenantId) {
        TenantContext tenant = tenantContextResolver.requireActiveMember(tenantId);
        if (permissionService.permissionCodes(tenant).contains("PROJECT:VIEW_ALL")) {
            return projectMapper.selectByTenantId(tenantId);
        }
        return projectMapper.selectAccessibleByMember(tenantId, tenant.userId());
    }

    public List<ProjectAccessContext> accessibleProjectContexts(Long tenantId) {
        TenantContext tenant = tenantContextResolver.requireActiveMember(tenantId);
        Set<String> permissions = permissionService.permissionCodes(tenant);
        List<ProjectEntity> projects = permissions.contains("PROJECT:VIEW_ALL")
            ? projectMapper.selectByTenantId(tenantId)
            : projectMapper.selectAccessibleByMember(tenantId, tenant.userId());
        return projects.stream().map(project -> requireView(tenant, project, permissions)).toList();
    }

    public MediaPage<ProjectAccessContext> accessibleProjectPage(Long tenantId, Integer current,
        Integer pageSize, String keyword) {
        TenantContext tenant = tenantContextResolver.requireActiveMember(tenantId);
        Set<String> permissions = permissionService.permissionCodes(tenant);
        boolean wide = permissions.contains("PROJECT:VIEW_ALL");
        PageBounds bounds = PageBounds.of(current, pageSize);
        String search = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = projectMapper.countVisible(tenantId, tenant.userId(), wide, search);
        List<ProjectEntity> projects = projectMapper.selectVisiblePage(tenantId, tenant.userId(), wide, search, bounds);
        if (projects.isEmpty()) return new MediaPage<>(List.of(), bounds.current(), bounds.pageSize(), total);
        Map<Long, List<ProjectBrowsePermission>> rows = wide ? Map.of() : projectMapper.selectBrowsePermissions(
            tenantId, tenant.userId(), projects.stream().map(project -> project.id).toList())
            .stream().collect(Collectors.groupingBy(row -> row.projectId));
        List<ProjectAccessContext> contexts = projects.stream().map(project -> {
            if (wide) return context(tenant, project, ProjectAccessSource.TENANT_WIDE, null, null, permissions);
            List<ProjectBrowsePermission> entries = rows.getOrDefault(project.id, List.of());
            Set<String> codes = entries.stream().map(row -> row.permissionCode).collect(Collectors.toSet());
            if (!codes.contains("PROJECT:VIEW")) return null;
            ProjectBrowsePermission row = entries.get(0);
            ProjectMemberEntity member = new ProjectMemberEntity();
            member.id = row.memberId;
            member.roleId = row.roleId;
            member.projectId = project.id;
            member.tenantId = tenantId;
            member.userId = tenant.userId();
            member.status = "ACTIVE";
            ProjectRoleEntity role = new ProjectRoleEntity();
            role.id = row.roleId;
            role.code = row.roleCode;
            role.name = row.roleName;
            role.projectId = project.id;
            role.tenantId = tenantId;
            role.status = "ACTIVE";
            return context(tenant, project, ProjectAccessSource.PROJECT_MEMBER, member, role, codes);
        }).filter(java.util.Objects::nonNull).toList();
        return new MediaPage<>(contexts, bounds.current(), bounds.pageSize(), total);
    }

    private ProjectAccessContext context(
        TenantContext tenant,
        ProjectEntity project,
        ProjectAccessSource source,
        ProjectMemberEntity member,
        ProjectRoleEntity role,
        Set<String> permissions
    ) {
        Set<String> effective = Set.copyOf(permissions);
        return new ProjectAccessContext(
            tenant,
            project,
            source,
            member,
            role,
            effective,
            ProjectCapabilities.from(effective, source)
        );
    }

    private BusinessException denied() {
        return new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目。");
    }
}
