package com.antshorttv.execution;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import org.springframework.stereotype.Service;

@Service
public class AiExecutionAccessService {
    private final AiExecutionService executionService;
    private final TenantContextResolver tenantContextResolver;
    private final ProjectAccessResolver projectAccessResolver;

    public AiExecutionAccessService(
        AiExecutionService executionService,
        TenantContextResolver tenantContextResolver,
        ProjectAccessResolver projectAccessResolver
    ) {
        this.executionService = executionService;
        this.tenantContextResolver = tenantContextResolver;
        this.projectAccessResolver = projectAccessResolver;
    }

    public AiExecutionTaskEntity requireView(Long tenantId, Long executionId) {
        tenantContextResolver.requireActiveMember(tenantId);
        AiExecutionTaskEntity task = executionService.requireTask(executionId);
        if (!tenantId.equals(task.tenantId)) {
            throw denied();
        }
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
        if (task.projectId != null) {
            projectAccessResolver.requireView(tenantId, task.projectId);
        }
        if (!context.userId().equals(task.userId)) {
            throw denied();
        }
        return task;
    }

    private BusinessException denied() {
        return new BusinessException(ErrorCode.FORBIDDEN, "No permission to access this AI execution.");
    }
}
