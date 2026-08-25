package com.antshorttv.project;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.Set;
import java.util.List;
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
        Set<String> projectPermissions = permissionService.projectPermissionCodes(tenant, projectId);
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
