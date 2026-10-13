package com.antshorttv.drama;

import com.antshorttv.common.*;
import com.antshorttv.rbac.TenantPermissionGuard;
import com.antshorttv.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/team-miniapps/authorization")
public class WechatAuthorizationController {
    private final WechatComponentService service;
    private final TenantPermissionGuard permissions;
    private final com.antshorttv.platform.PlatformPermissionGuard platform;
    public WechatAuthorizationController(WechatComponentService service, TenantPermissionGuard permissions,
        com.antshorttv.platform.PlatformPermissionGuard platform) {
        this.service = service; this.permissions = permissions; this.platform = platform;
    }
    private TenantContext context(HttpServletRequest request) {
        platform.requireAdministrator();
        return permissions.require(TenantRequestSupport.tenantId(request), "MINIAPP:MANAGE");
    }
    @GetMapping("/status") public ApiResponse<WechatComponentService.Status> status(HttpServletRequest request) {
        context(request); return ApiResponse.success(service.status());
    }
    @PostMapping("/requests") public ApiResponse<WechatComponentService.Created> create(HttpServletRequest request) {
        var c = context(request); return ApiResponse.success(service.create(c.tenantId(), c.userId()));
    }
    @GetMapping("/requests/{id}") public ApiResponse<WechatComponentService.RequestStatus> request(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(service.request(context(request).tenantId(), id));
    }
}
