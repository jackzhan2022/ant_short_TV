package com.antshorttv.ai;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
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

    @GetMapping("/ai-service-configs")
    @RequirePermission("AI_SERVICE:VIEW")
    public ApiResponse<List<AiServiceConfigResponse>> listGlobal(HttpServletRequest servletRequest) {
        return ApiResponse.success(aiServiceConfigService.list(requireTenantId(servletRequest)));
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

    @PostMapping("/ai-service-configs")
    @RequirePermission("AI_SERVICE:CREATE")
    public ApiResponse<AiServiceConfigResponse> createGlobal(
        @Valid @RequestBody AiServiceConfigRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.create(requireTenantId(servletRequest), request, servletRequest));
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

    @PutMapping("/ai-service-configs/{id}")
    @RequirePermission("AI_SERVICE:EDIT")
    public ApiResponse<AiServiceConfigResponse> updateGlobal(
        @PathVariable Long id,
        @Valid @RequestBody AiServiceConfigRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.update(requireTenantId(servletRequest), id, request, servletRequest));
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

    @PutMapping("/ai-service-configs/{id}/status")
    @RequirePermission("AI_SERVICE:EDIT")
    public ApiResponse<AiServiceConfigResponse> updateStatusGlobal(
        @PathVariable Long id,
        @Valid @RequestBody AiServiceStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.updateStatus(requireTenantId(servletRequest), id, request, servletRequest));
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

    @PutMapping("/ai-service-configs/{id}/default")
    @RequirePermission("AI_SERVICE:EDIT")
    public ApiResponse<AiServiceConfigResponse> setDefaultGlobal(
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.setDefault(requireTenantId(servletRequest), id, servletRequest));
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

    @PostMapping("/ai-service-configs/{id}/test")
    @RequirePermission("AI_SERVICE:TEST")
    public ApiResponse<AiServiceTestResponse> testGlobal(
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(aiServiceConfigService.test(requireTenantId(servletRequest), id, servletRequest));
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

    @DeleteMapping("/ai-service-configs/{id}")
    @RequirePermission("AI_SERVICE:DELETE")
    public ApiResponse<Void> deleteGlobal(
        @PathVariable Long id,
        HttpServletRequest servletRequest
    ) {
        aiServiceConfigService.delete(requireTenantId(servletRequest), id, servletRequest);
        return ApiResponse.ok();
    }

    private Long requireTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少团队上下文。");
        }
        try {
            return Long.valueOf(tenantId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前创作团队标识不正确。");
        }
    }
}
