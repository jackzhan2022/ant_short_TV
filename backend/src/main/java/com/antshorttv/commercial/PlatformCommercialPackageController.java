package com.antshorttv.commercial;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/commercial/packages")
public class PlatformCommercialPackageController {
    private final CommercialPackageService service;
    public PlatformCommercialPackageController(CommercialPackageService service) { this.service = service; }

    @GetMapping @RequirePlatformPermission("PLATFORM_COMMERCIAL_ORDER_VIEW") public ApiResponse<List<CommercialPackageSummaryResponse>> list() { return ApiResponse.success(service.listPackages()); }
    @GetMapping("/{packageId}/versions") @RequirePlatformPermission("PLATFORM_COMMERCIAL_ORDER_VIEW") public ApiResponse<List<CommercialPackageVersionResponse>> history(@PathVariable Long packageId) { return ApiResponse.success(service.history(packageId)); }
    @PostMapping @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT") public ApiResponse<CommercialPackageVersionResponse> createDraft(@RequestBody CommercialPackageDraftCommand request) { return ApiResponse.success(service.createDraft(request)); }
    @PostMapping("/{packageId}/versions/{versionId}/publish") @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT") public ApiResponse<CommercialPackageVersionResponse> publish(@PathVariable Long packageId, @PathVariable Long versionId) { return ApiResponse.success(service.publish(packageId, versionId, null)); }
    @PostMapping("/{packageId}/versions/{versionId}/unpublish") @RequirePlatformPermission("PLATFORM_COMMERCIAL_PACKAGE_EDIT") public ApiResponse<CommercialPackageVersionResponse> unpublish(@PathVariable Long packageId, @PathVariable Long versionId) { return ApiResponse.success(service.unpublish(packageId, versionId)); }
}
