package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial/entitlements")
public class PlatformCommercialEntitlementController {
    private final CommercialEntitlementCatalogService service;

    public PlatformCommercialEntitlementController(CommercialEntitlementCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_ORDER_VIEW")
    public ApiResponse<List<CommercialEntitlementDefinitionResponse>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT")
    public ApiResponse<CommercialEntitlementDefinitionResponse> create(
        @RequestBody CommercialDisplayEntitlementCommand request
    ) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT")
    public ApiResponse<CommercialEntitlementDefinitionResponse> update(
        @PathVariable Long id,
        @RequestBody CommercialDisplayEntitlementCommand request
    ) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/enable")
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT")
    public ApiResponse<CommercialEntitlementDefinitionResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(service.enable(id));
    }

    @PostMapping("/{id}/disable")
    @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT")
    public ApiResponse<CommercialEntitlementDefinitionResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(service.disable(id));
    }
}
