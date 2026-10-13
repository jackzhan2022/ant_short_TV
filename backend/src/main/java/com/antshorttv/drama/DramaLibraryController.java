package com.antshorttv.drama;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.rbac.TenantPermissionGuard;
import com.antshorttv.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drama-library")
public class DramaLibraryController {
    private final DramaLibraryService service;
    private final TenantPermissionGuard permissions;
    private final com.antshorttv.platform.PlatformPermissionGuard platform;
    public DramaLibraryController(DramaLibraryService service, TenantPermissionGuard permissions,
        com.antshorttv.platform.PlatformPermissionGuard platform) {
        this.service = service; this.permissions = permissions; this.platform = platform;
    }
    @GetMapping public ApiResponse<List<Drama>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(context(request, "VIEW").tenantId()));
    }
    @GetMapping("/{id}") public ApiResponse<Drama> get(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.get(context(request, "VIEW").tenantId(), id));
    }
    @PostMapping public ApiResponse<Drama> create(@Valid @RequestBody DramaRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.create(context(request, "MANAGE").tenantId(), body));
    }
    @PutMapping("/{id}") public ApiResponse<Drama> update(@PathVariable Long id, @Valid @RequestBody DramaRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(context(request, "MANAGE").tenantId(), id, body));
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(context(request, "MANAGE").tenantId(), id); return ApiResponse.ok();
    }
    @GetMapping("/{id}/episodes") public ApiResponse<List<Episode>> episodes(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.episodes(context(request, "VIEW").tenantId(), id));
    }
    @PostMapping("/{id}/episodes") public ApiResponse<Episode> importEpisode(@PathVariable Long id,
        @Valid @RequestBody EpisodeRequest body, HttpServletRequest request) {
        TenantContext context = context(request, "MANAGE");
        return ApiResponse.success(service.importEpisode(context.tenantId(), context.userId(), id, body));
    }
    @DeleteMapping("/{id}/episodes/{episodeId}") public ApiResponse<Void> deleteEpisode(@PathVariable Long id,
        @PathVariable Long episodeId, HttpServletRequest request) {
        service.deleteEpisode(context(request, "MANAGE").tenantId(), id, episodeId); return ApiResponse.ok();
    }
    private TenantContext context(HttpServletRequest request, String action) {
        platform.requireAdministrator();
        return permissions.require(TenantRequestSupport.tenantId(request), "DRAMA:" + action);
    }
}
