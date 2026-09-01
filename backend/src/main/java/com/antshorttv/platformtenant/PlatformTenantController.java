package com.antshorttv.platformtenant;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/tenants")
public class PlatformTenantController {
    private final PlatformTenantService service;

    public PlatformTenantController(PlatformTenantService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePlatformPermission("PLATFORM_TENANT_VIEW")
    public ApiResponse<PlatformTenantPageResponse> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String packageType,
        @RequestParam(required = false) Integer current,
        @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(service.list(
            PlatformTenantQuery.of(keyword, status, packageType, current, pageSize)));
    }

    @GetMapping("/{tenantId:\\d+}")
    @RequirePlatformPermission("PLATFORM_TENANT_VIEW")
    public ApiResponse<PlatformTenantDetailResponse> detail(@PathVariable Long tenantId) {
        return ApiResponse.success(service.detail(tenantId));
    }

    @PutMapping("/{tenantId:\\d+}/status")
    @RequirePlatformPermission("PLATFORM_TENANT_STATUS_EDIT")
    public ApiResponse<PlatformTenantSummaryResponse> updateStatus(
        @PathVariable Long tenantId,
        @Valid @RequestBody UpdatePlatformTenantStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(service.updateStatus(tenantId, request, servletRequest));
    }
}
