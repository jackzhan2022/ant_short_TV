package com.antshorttv.rbac;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectAccessSource;
import com.antshorttv.security.TenantContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProjectPermissionGuard {

    private static final Map<String, String> TENANT_WIDE_PERMISSIONS = Map.of(
        "PROJECT:VIEW", "PROJECT:VIEW_ALL",
        "PROJECT:EDIT", "PROJECT:EDIT_ALL",
        "PROJECT:DELETE", "PROJECT:DELETE_ALL"
    );

    private final ProjectAccessResolver projectAccessResolver;

    public ProjectPermissionGuard(ProjectAccessResolver projectAccessResolver) {
        this.projectAccessResolver = projectAccessResolver;
    }

    public TenantContext require(Long tenantId, Long projectId, String permissionCode) {
        if (tenantId == null || projectId == null || permissionCode == null) {
            throw forbidden();
        }
        ProjectAccessContext access = projectAccessResolver.requireView(tenantId, projectId);
        if (access == null) {
            throw forbidden();
        }
        String tenantWidePermission = TENANT_WIDE_PERMISSIONS.get(permissionCode);
        if (access.source() == ProjectAccessSource.TENANT_WIDE) {
            String required = tenantWidePermission == null ? permissionCode : tenantWidePermission;
            if (access.effectivePermissions().contains(required)) {
                return access.tenant();
            }
        }
        if (access.source() == ProjectAccessSource.PROJECT_MEMBER
            && access.effectivePermissions().contains(permissionCode)) {
            return access.tenant();
        }
        throw forbidden();
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目。");
    }
}
