package com.antshorttv.desktop;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.antshorttv.desktop.DesktopSourceModels.*;

@RestController
@RequestMapping("/api/desktop/source-projects")
public class DesktopSourceController {
    private final DesktopSourceService service;
    public DesktopSourceController(DesktopSourceService service) { this.service=service; }

    @GetMapping
    public ApiResponse<List<SourceProject>> list(HttpServletRequest request) {
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request)));
    }
    @PostMapping("/sync")
    public ApiResponse<SourceProject> sync(HttpServletRequest request,@RequestBody SyncRequest body) {
        return ApiResponse.success(service.sync(TenantRequestSupport.tenantId(request),body));
    }
    @GetMapping("/{id}/assets")
    public ApiResponse<List<SourceAsset>> assets(HttpServletRequest request,@PathVariable Long id) {
        return ApiResponse.success(service.assets(TenantRequestSupport.tenantId(request),id));
    }
    @PutMapping("/{id}/binding")
    public ApiResponse<SourceProject> bind(HttpServletRequest request,@PathVariable Long id,@RequestBody BindingRequest body) {
        return ApiResponse.success(service.bind(TenantRequestSupport.tenantId(request),id,body.projectId()));
    }
    @PostMapping("/{id}/match")
    public ApiResponse<List<SourceAsset>> match(HttpServletRequest request,@PathVariable Long id) {
        return ApiResponse.success(service.match(TenantRequestSupport.tenantId(request),id));
    }
    @GetMapping("/{id}/candidates")
    public ApiResponse<List<Candidate>> candidates(HttpServletRequest request,@PathVariable Long id,@RequestParam String type,
                                                   @RequestParam(required=false) String sourceParentId) {
        return ApiResponse.success(service.candidates(TenantRequestSupport.tenantId(request),id,type,sourceParentId));
    }
    @PutMapping("/assets/{id}/match")
    public ApiResponse<SourceAsset> manualMatch(HttpServletRequest request,@PathVariable Long id,@RequestBody MatchRequest body) {
        return ApiResponse.success(service.manualMatch(TenantRequestSupport.tenantId(request),id,body.assetId(),body.variantId()));
    }
    @PostMapping("/prompts")
    public ResponseEntity<ApiResponse<List<Prompt>>> prompts(HttpServletRequest request,@RequestBody PromptRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(service.prompts(TenantRequestSupport.tenantId(request),body.assetIds())));
    }
}
