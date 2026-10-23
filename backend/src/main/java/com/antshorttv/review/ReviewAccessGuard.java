package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
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
        String menu = permissionService.hasPermission(context, "MENU:SCRIPT_REVIEW")
            ? "MENU:SCRIPT_REVIEW" : "MENU:PRODUCTION_TASKS";
        if (!permissionService.hasPermission(context, menu)) throw forbidden();
        if (reviewProject.getMainProjectId() != null) {
            projectPermissionGuard.require(
                context.tenantId(),
                reviewProject.getMainProjectId(),
                menu
            );
            return;
        }
        if (context.userId().equals(reviewProject.getCreatedBy())) {
            return;
        }
        if (!permissionService.canAccessAllProjects(context)) {
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
        projectPermissionGuard.require(context.tenantId(), targetMainProjectId, "MENU:SCRIPT_REVIEW");
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该审阅稿。");
    }
}
