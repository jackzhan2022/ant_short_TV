package com.antshorttv.rbac;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequirePermissionAspect {

    private final TenantContextResolver tenantContextResolver;
    private final RbacPermissionService rbacPermissionService;

    public RequirePermissionAspect(TenantContextResolver tenantContextResolver, RbacPermissionService rbacPermissionService) {
        this.tenantContextResolver = tenantContextResolver;
        this.rbacPermissionService = rbacPermissionService;
    }

    @Before("@annotation(requirePermission)")
    public void require(JoinPoint joinPoint, RequirePermission requirePermission) {
        Long tenantId = resolveTenantId(joinPoint.getArgs());
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        Long projectId = resolveProjectId(joinPoint.getArgs());
        if (!rbacPermissionService.hasPermission(context, requirePermission.value(), projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行该操作。");
        }
    }

    private Long resolveTenantId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest request) {
                Long tenantIdFromPath = resolveTenantIdFromPath(request);
                if (tenantIdFromPath != null) {
                    return tenantIdFromPath;
                }
                String tenantId = request.getHeader("X-Tenant-Id");
                if (tenantId != null && !tenantId.isBlank()) {
                    try {
                        return Long.valueOf(tenantId);
                    } catch (NumberFormatException exception) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前创作团队标识不正确。");
                    }
                }
            }
        }
        for (Object arg : args) {
            if (arg instanceof Long value) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少团队上下文。");
    }

    private Long resolveTenantIdFromPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/api/tenants/";
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        String rest = uri.substring(prefix.length());
        String firstSegment = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        try {
            return Long.valueOf(firstSegment);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前创作团队标识不正确。");
        }
    }

    private Long resolveProjectId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest request) {
                String uri = request.getRequestURI();
                String prefix = "/api/projects/";
                if (uri != null && uri.startsWith(prefix)) {
                    String rest = uri.substring(prefix.length());
                    String firstSegment = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                    try {
                        return Long.valueOf(firstSegment);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
