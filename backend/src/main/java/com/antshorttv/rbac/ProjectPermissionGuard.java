package com.antshorttv.rbac;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.security.TenantContext;
import org.springframework.stereotype.Component;

@Component
public class ProjectPermissionGuard {

    private final ProjectAccessResolver projectAccessResolver;

    public ProjectPermissionGuard(ProjectAccessResolver projectAccessResolver) {
        this.projectAccessResolver = projectAccessResolver;
    }

    public TenantContext require(Long tenantId, Long projectId, String permissionCode) {
        if (tenantId == null || projectId == null || permissionCode == null) {
            throw forbidden();
        }
        return require(projectAccessResolver.requireView(tenantId, projectId), permissionCode);
    }

    public TenantContext require(ProjectAccessContext access, String permissionCode) {
        if (permissionCode == null) {
            throw forbidden();
        }
        if (access == null) {
            throw forbidden();
        }
        if (MenuAuthorization.allows(access.effectivePermissions(), permissionCode)) {
            return access.tenant();
        }
        throw forbidden();
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目。");
    }
}
