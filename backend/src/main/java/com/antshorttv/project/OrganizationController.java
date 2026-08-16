package com.antshorttv.project;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @RequirePermission("ORGANIZATION:VIEW")
    public ApiResponse<List<OrganizationResponse>> tree(HttpServletRequest request) {
        return ApiResponse.success(organizationService.tree(tenantId(request)));
    }

    @PostMapping
    @RequirePermission("ORGANIZATION:CREATE")
    public ApiResponse<OrganizationResponse> create(
        @Valid @RequestBody CreateOrganizationRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(organizationService.create(tenantId(request), body, request));
    }

    @GetMapping("/{id}")
    @RequirePermission("ORGANIZATION:VIEW")
    public ApiResponse<OrganizationResponse> detail(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(organizationService.detail(tenantId(request), id));
    }

    @PutMapping("/{id}")
    @RequirePermission("ORGANIZATION:EDIT")
    public ApiResponse<OrganizationResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateOrganizationRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(organizationService.update(tenantId(request), id, body, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("ORGANIZATION:DELETE")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        organizationService.delete(tenantId(request), id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("ORGANIZATION:EDIT")
    public ApiResponse<OrganizationResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateOrganizationStatusRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(organizationService.updateStatus(tenantId(request), id, body, request));
    }

    @PutMapping("/{id}/leader")
    @RequirePermission("ORGANIZATION:EDIT")
    public ApiResponse<OrganizationResponse> updateLeader(
        @PathVariable Long id,
        @RequestBody UpdateOrganizationLeaderRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(organizationService.updateLeader(tenantId(request), id, body, request));
    }

    private Long tenantId(HttpServletRequest request) {
        return Long.valueOf(request.getHeader("X-Tenant-Id"));
    }
}
