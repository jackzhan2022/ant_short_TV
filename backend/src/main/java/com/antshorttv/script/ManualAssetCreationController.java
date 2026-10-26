package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/manual-assets")
public class ManualAssetCreationController {
    private final ManualAssetCreationService service;
    public ManualAssetCreationController(ManualAssetCreationService service) { this.service=service; }
    @GetMapping("/configuration")
    @RequireProjectPermission("ELEMENT:VIEW")
    public ApiResponse<ManualAssetCreationService.Options> configuration(@PathVariable Long projectId,HttpServletRequest request) {
        return ApiResponse.success(service.options(TenantRequestSupport.tenantId(request),projectId));
    }
    @PostMapping
    @RequireProjectPermission("ELEMENT:EDIT")
    public ApiResponse<ManualAssetCreationService.Created> create(@PathVariable Long projectId,
        @RequestBody ManualAssetCreationService.Command command,HttpServletRequest request) {
        return ApiResponse.success(service.create(TenantRequestSupport.tenantId(request),projectId,command));
    }
}
