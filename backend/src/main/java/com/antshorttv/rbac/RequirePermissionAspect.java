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
public class RequirePermissionAspect {

    private final TenantPermissionGuard tenantPermissionGuard;

    public RequirePermissionAspect(TenantPermissionGuard tenantPermissionGuard) {
        this.tenantPermissionGuard = tenantPermissionGuard;
    }

    @Before("@annotation(requirePermission)")
    public void require(JoinPoint joinPoint, RequirePermission requirePermission) {
        Long tenantId = resolveDeclaredTenantId(joinPoint, requirePermission.tenantIdParameter());
        if (tenantId == null) {
            tenantId = resolveHeaderTenantId(joinPoint.getArgs());
        }
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少有效的团队权限上下文。");
        }
        tenantPermissionGuard.require(tenantId, requirePermission.value());
    }

    private Long resolveDeclaredTenantId(JoinPoint joinPoint, String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return null;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        for (int index = 0; index < arguments.length; index++) {
            if (parameterName.equals(parameterNames[index])) {
                if (arguments[index] instanceof Long value) {
                    return value;
                }
                throw new BusinessException(ErrorCode.FORBIDDEN, "缺少有效的团队权限上下文。");
            }
        }
        return null;
    }

    private Long resolveHeaderTenantId(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof HttpServletRequest request) {
                return TenantRequestSupport.tenantId(request);
            }
        }
        return null;
    }
}
