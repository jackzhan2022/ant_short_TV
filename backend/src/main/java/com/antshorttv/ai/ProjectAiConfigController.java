package com.antshorttv.ai;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class ProjectAiConfigController {
    private final ProjectAiConfigService service;

    public ProjectAiConfigController(ProjectAiConfigService service) {
        this.service = service;
    }

    @GetMapping("/models")
    @RequirePermission("PROJECT_AI_CONFIG_VIEW")
    public ApiResponse<ProjectAiModelsResponse> models(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.availableModels(requireTenantId(request), projectId));
    }

    @GetMapping("/config")
    @RequirePermission("PROJECT_AI_CONFIG_VIEW")
    public ApiResponse<ProjectAiConfigResponse> config(@PathVariable Long projectId, HttpServletRequest request) {
        return ApiResponse.success(service.config(requireTenantId(request), projectId));
    }

    @PutMapping("/config")
    @RequirePermission("PROJECT_AI_CONFIG_EDIT")
    public ApiResponse<ProjectAiConfigResponse> save(@PathVariable Long projectId, @RequestBody ProjectAiConfigRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.save(requireTenantId(request), projectId, body, request));
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
