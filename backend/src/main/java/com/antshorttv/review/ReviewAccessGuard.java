package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ReviewAccessGuard {

    private final ProjectPermissionGuard projectPermissionGuard;
    private final RbacPermissionService permissionService;

    public ReviewAccessGuard(
        ProjectPermissionGuard projectPermissionGuard,
        RbacPermissionService permissionService
    ) {
        this.projectPermissionGuard = projectPermissionGuard;
        this.permissionService = permissionService;
    }

    public void require(TenantContext context, ReviewProjectEntity reviewProject, String permissionCode) {
        if (context == null
            || reviewProject == null
            || !context.tenantId().equals(reviewProject.getTenantId())) {
            throw forbidden();
        }
        if (reviewProject.getMainProjectId() != null) {
            projectPermissionGuard.require(
                context.tenantId(),
                reviewProject.getMainProjectId(),
                permissionCode
            );
            return;
        }
        if (context.userId().equals(reviewProject.getCreatedBy())) {
            return;
        }
        Set<String> tenantPermissions = permissionService.permissionCodes(context);
        String tenantWidePermission = "PROJECT:VIEW".equals(permissionCode)
            ? "PROJECT:VIEW_ALL"
            : "PROJECT:EDIT_ALL";
        if (!tenantPermissions.contains(tenantWidePermission)) {
            throw forbidden();
        }
    }

    public boolean canView(TenantContext context, ReviewProjectEntity reviewProject) {
        try {
            require(context, reviewProject, "PROJECT:VIEW");
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    public void requireBinding(
        TenantContext context,
        ReviewProjectEntity reviewProject,
        Long targetMainProjectId
    ) {
        require(context, reviewProject, "SCRIPT:EDIT");
        if (reviewProject.getMainProjectId() != null || targetMainProjectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "审阅稿已绑定项目，不能重新绑定。");
        }
        projectPermissionGuard.require(context.tenantId(), targetMainProjectId, "SCRIPT:EDIT");
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该审阅稿。");
    }
}
