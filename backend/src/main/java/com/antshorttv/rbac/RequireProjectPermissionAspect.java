package com.antshorttv.rbac;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireProjectPermissionAspect {

    private final ProjectPermissionGuard projectPermissionGuard;

    public RequireProjectPermissionAspect(ProjectPermissionGuard projectPermissionGuard) {
        this.projectPermissionGuard = projectPermissionGuard;
    }

    @Before("@annotation(requirePermission)")
    public void require(JoinPoint joinPoint, RequireProjectPermission requirePermission) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        Long projectId = null;
        HttpServletRequest request = null;
        for (int index = 0; index < arguments.length; index++) {
            if (requirePermission.projectIdParameter().equals(parameterNames[index])) {
                if (!(arguments[index] instanceof Long value)) {
                    throw invalidContext();
                }
                projectId = value;
            }
            if (arguments[index] instanceof HttpServletRequest servletRequest) {
                request = servletRequest;
            }
        }
        if (projectId == null || request == null) {
            throw invalidContext();
        }
        Long tenantId = TenantRequestSupport.tenantId(request);
        for (String permission : requirePermission.value()) {
            projectPermissionGuard.require(tenantId, projectId, permission);
        }
    }

    private BusinessException invalidContext() {
        return new BusinessException(ErrorCode.FORBIDDEN, "缺少有效的项目权限上下文。");
    }
}
