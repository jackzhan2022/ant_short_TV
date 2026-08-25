package com.antshorttv.rbac;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantPermissionGuard {

    private final TenantContextResolver tenantContextResolver;
    private final RbacPermissionService permissionService;

    public TenantPermissionGuard(
        TenantContextResolver tenantContextResolver,
        RbacPermissionService permissionService
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.permissionService = permissionService;
    }

    public TenantContext require(Long tenantId, String permissionCode) {
        if (tenantId == null) {
            throw forbidden();
        }
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        if (permissionCode == null || !permissionService.hasPermission(context, permissionCode)) {
            throw forbidden();
        }
        return context;
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "无权执行该团队操作。");
    }
}
