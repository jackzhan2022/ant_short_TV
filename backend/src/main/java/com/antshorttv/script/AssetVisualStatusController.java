package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/visual-variants")
public class AssetVisualStatusController {
    private final AssetVisualStatusService service;
    public AssetVisualStatusController(AssetVisualStatusService service) { this.service = service; }

    @GetMapping("/generation-status")
    public ApiResponse<List<AssetVisualStatusService.Status>> read(
        @PathVariable Long projectId, @RequestParam List<Long> variantIds, HttpServletRequest request
    ) {
        return ApiResponse.success(service.read(TenantRequestSupport.tenantId(request), projectId, variantIds));
    }
}
