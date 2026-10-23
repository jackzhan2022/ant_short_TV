package com.antshorttv.material;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@com.antshorttv.rbac.RequirePermission("MENU:ASSET_LIBRARY")
@RestController
@RequestMapping("/api/projects/{projectId}/asset-library")
public class ProjectAssetLibraryController {
    private final ProjectAssetLibraryService service;
    public ProjectAssetLibraryController(ProjectAssetLibraryService service) { this.service = service; }

    @GetMapping
    public ApiResponse<ProjectAssetLibraryService.LibraryResponse> list(
        @PathVariable Long projectId, @RequestParam(defaultValue="IMAGE") String mediaType,
        @RequestParam(required=false) String category, @RequestParam(required=false) Integer episodeNo,
        @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer current,
        @RequestParam(required=false) Integer pageSize, HttpServletRequest request
    ) {
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request), projectId,
            mediaType, category, episodeNo, keyword, current, pageSize));
    }
}
