package com.antshorttv.platform;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import org.springframework.stereotype.Component;

@Component
public class PlatformPermissionGuard {

    private final CurrentPrincipal currentPrincipal;
    private final PlatformAuthorizationService authorizationService;

    public PlatformPermissionGuard(
        CurrentPrincipal currentPrincipal,
        PlatformAuthorizationService authorizationService
    ) {
        this.currentPrincipal = currentPrincipal;
        this.authorizationService = authorizationService;
    }

    public void requireAdministrator() {
        Long userId = currentPrincipal.require().userId();
        if (!authorizationService.roleCodes(userId).contains("PLATFORM_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅平台管理员可访问此管理功能。");
        }
    }

    public void require(String permissionCode) {
        Long userId = currentPrincipal.require().userId();
        if (!authorizationService.hasPermission(userId, permissionCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问平台管理功能。");
        }
    }
}
