package com.antshorttv.execution;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.rbac.MenuAuthorization;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import org.springframework.stereotype.Service;

@Service
public class AiExecutionAccessService {
    private final AiExecutionService executionService;
    private final TenantContextResolver tenantContextResolver;
    private final ProjectAccessResolver projectAccessResolver;
    private final RbacPermissionService permissions;

    public AiExecutionAccessService(
        AiExecutionService executionService,
        TenantContextResolver tenantContextResolver,
        ProjectAccessResolver projectAccessResolver,
        RbacPermissionService permissions
    ) {
        this.executionService = executionService;
        this.tenantContextResolver = tenantContextResolver;
        this.projectAccessResolver = projectAccessResolver;
        this.permissions = permissions;
    }

    public AiExecutionTaskEntity requireView(Long tenantId, Long executionId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        AiExecutionTaskEntity task = executionService.requireTask(executionId);
        if (!tenantId.equals(task.tenantId)) {
            throw denied();
        }
        requireMenu(context, task, false);
        if (task.projectId == null && ("REVIEW_TASK".equals(task.businessType)
            || "VIDEO_DECOMPOSITION_EPISODE".equals(task.businessType))
            && !context.userId().equals(task.userId) && !permissions.canAccessAllProjects(context)) throw denied();
        if (task.projectId != null) {
            projectAccessResolver.requireView(tenantId, task.projectId);
        }
        return task;
    }

    public AiExecutionTaskEntity requireControl(Long tenantId, Long executionId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        AiExecutionTaskEntity task = executionService.requireTask(executionId);
        if (!tenantId.equals(task.tenantId)) {
            throw denied();
        }
        requireMenu(context, task, true);
        if (task.projectId != null) {
            projectAccessResolver.requireView(tenantId, task.projectId);
        }
        if (!context.userId().equals(task.userId)) {
            throw denied();
        }
        return task;
    }

    private void requireMenu(TenantContext context, AiExecutionTaskEntity task, boolean control) {
        var menus = permissions.permissionCodes(context);
        boolean allowed = menus.contains("MENU:PRODUCTION_TASKS");
        if ("FREE_CREATION".equals(task.businessType)) allowed |= menus.contains("MENU:FREE_CREATION");
        else if ("REVIEW_TASK".equals(task.businessType)) allowed |= menus.contains("MENU:SCRIPT_REVIEW");
        else if ("VIDEO_DECOMPOSITION_EPISODE".equals(task.businessType)) allowed |= menus.contains("MENU:VIDEO_DECOMPOSITION");
        else allowed |= control ? MenuAuthorization.canWorkOnProject(menus) : MenuAuthorization.canReadProject(menus);
        if (!allowed) throw denied();
    }

    private BusinessException denied() {
        return new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this AI execution.");
    }
}
