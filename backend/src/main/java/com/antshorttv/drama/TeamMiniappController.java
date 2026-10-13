package com.antshorttv.drama;

import com.antshorttv.common.*;
import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.rbac.TenantPermissionGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/team-miniapps")
public class TeamMiniappController {
    private final DramaLibraryService service;
    private final TenantPermissionGuard permissions;
    private final com.antshorttv.platform.PlatformPermissionGuard platform;
    public TeamMiniappController(DramaLibraryService service, TenantPermissionGuard permissions,
        com.antshorttv.platform.PlatformPermissionGuard platform) {
        this.service = service; this.permissions = permissions; this.platform = platform;
    }
    @GetMapping public ApiResponse<List<Miniapp>> list(HttpServletRequest request) {
        return ApiResponse.success(service.miniapps(tenant(request, "DRAMA:VIEW")));
    }
    @PostMapping public ApiResponse<Miniapp> bind(@Valid @RequestBody BindingRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.bind(tenant(request, "MINIAPP:MANAGE"), body));
    }
    @PatchMapping("/{id}") public ApiResponse<Miniapp> enable(@PathVariable Long id,
        @Valid @RequestBody BindingUpdate body, HttpServletRequest request) {
        return ApiResponse.success(service.enable(tenant(request, "MINIAPP:MANAGE"), id, body.enabled()));
    }
    @GetMapping("/{id}/dramas") public ApiResponse<List<Release>> releases(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.releases(tenant(request, "DRAMA:VIEW"), id));
    }
    @PutMapping("/{id}/dramas/{dramaId}") public ApiResponse<Release> publish(@PathVariable Long id,
        @PathVariable Long dramaId, @Valid @RequestBody PublishRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.publish(tenant(request, "DRAMA:PUBLISH"), id, dramaId, body));
    }
    @DeleteMapping("/{id}/dramas/{dramaId}") public ApiResponse<Void> unpublish(@PathVariable Long id,
        @PathVariable Long dramaId, HttpServletRequest request) {
        service.unpublish(tenant(request, "DRAMA:PUBLISH"), id, dramaId); return ApiResponse.ok();
    }
    private Long tenant(HttpServletRequest request, String permission) {
        platform.requireAdministrator();
        return permissions.require(TenantRequestSupport.tenantId(request), permission).tenantId();
    }
}
