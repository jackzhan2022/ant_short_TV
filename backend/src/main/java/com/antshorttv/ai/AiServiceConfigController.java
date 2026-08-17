package com.antshorttv.ai;

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
@RequestMapping("/api")
public class AiServiceConfigController {

    private final AiServiceConfigService aiServiceConfigService;

    public AiServiceConfigController(AiServiceConfigService aiServiceConfigService) {
        this.aiServiceConfigService = aiServiceConfigService;
    }

    @GetMapping("/ai-providers")
    public ApiResponse<List<AiProviderResponse>> providers() {
        return ApiResponse.success(aiServiceConfigService.providers());
    }

    @GetMapping("/tenants/{tenantId}/ai-service-configs")
    @RequirePermission("AI_SERVICE:VIEW")
    public ApiResponse<List<AiServiceConfigResponse>> list(@PathVariable Long tenantId) {
        return ApiResponse.success(aiServiceConfigService.list(tenantId));
    }

    @PostMapping("/tenants/{tenantId}/ai-service-configs")
    @RequirePermission("AI_SERVICE:CREATE")
    public ApiResponse<AiServiceConfigResponse> create(
        @PathVariable Long tenantId,
        @Valid @RequestBody AiServiceConfigRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.create(tenantId, request, servletRequest));
    }

    @PutMapping("/tenants/{tenantId}/ai-service-configs/{id}")
    @RequirePermission("AI_SERVICE:EDIT")
    public ApiResponse<AiServiceConfigResponse> update(
        @PathVariable Long tenantId,
        @PathVariable Long id,
        @Valid @RequestBody AiServiceConfigRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.update(tenantId, id, request, servletRequest));
    }

    @PutMapping("/tenants/{tenantId}/ai-service-configs/{id}/status")
    @RequirePermission("AI_SERVICE:EDIT")
    public ApiResponse<AiServiceConfigResponse> updateStatus(
        @PathVariable Long tenantId,
        @PathVariable Long id,
        @Valid @RequestBody AiServiceStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.updateStatus(tenantId, id, request, servletRequest));
    }

    @PutMapping("/tenants/{tenantId}/ai-service-configs/{id}/default")
    @RequirePermission("AI_SERVICE:EDIT")
    public ApiResponse<AiServiceConfigResponse> setDefault(
        @PathVariable Long tenantId,
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.setDefault(tenantId, id, servletRequest));
    }

    @PostMapping("/tenants/{tenantId}/ai-service-configs/{id}/test")
    @RequirePermission("AI_SERVICE:TEST")
    public ApiResponse<AiServiceTestResponse> test(
        @PathVariable Long tenantId,
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.test(tenantId, id, servletRequest));
    }

    @DeleteMapping("/tenants/{tenantId}/ai-service-configs/{id}")
    @RequirePermission("AI_SERVICE:DELETE")
    public ApiResponse<Void> delete(
        @PathVariable Long tenantId,
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        aiServiceConfigService.delete(tenantId, id, servletRequest);
        return ApiResponse.ok();
    }
}
