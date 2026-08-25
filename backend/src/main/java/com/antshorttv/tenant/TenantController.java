package com.antshorttv.tenant;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ApiResponse<TenantSummaryResponse> create(
        @Valid @RequestBody CreateTenantRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantService.create(request, servletRequest));
    }

    @GetMapping("/my")
    public ApiResponse<List<TenantSummaryResponse>> myTenants() {
        return ApiResponse.success(tenantService.myTenants());
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission(value = "TENANT:VIEW", tenantIdParameter = "id")
    public ApiResponse<TenantSummaryResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(tenantService.detail(id));
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission(value = "TENANT:EDIT", tenantIdParameter = "id")
    public ApiResponse<TenantSummaryResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateTenantRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantService.update(id, request, servletRequest));
    }

    @PutMapping("/{id:\\d+}/status")
    @RequirePermission(value = "TENANT:EDIT", tenantIdParameter = "id")
    public ApiResponse<TenantSummaryResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateTenantStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(tenantService.updateStatus(id, request, servletRequest));
    }

}
