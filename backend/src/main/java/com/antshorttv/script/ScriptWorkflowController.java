package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ScriptWorkflowController {

    private final ScriptWorkflowService scriptWorkflowService;

    public ScriptWorkflowController(ScriptWorkflowService scriptWorkflowService) {
        this.scriptWorkflowService = scriptWorkflowService;
    }

    @GetMapping("/script-workspace")
    @RequirePermission("PROJECT:VIEW")
    public ApiResponse<ScriptWorkspaceResponse> workspace(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.workspace(tenantId(request), projectId));
    }

    @PostMapping("/scripts/ai-generate")
    @RequirePermission("AI_SERVICE:USE")
    public ApiResponse<ScriptWorkspaceResponse> generate(
        @PathVariable Long projectId,
        @Valid @RequestBody GenerateScriptRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.generate(tenantId(request), projectId, body, request));
    }

    private Long tenantId(HttpServletRequest request) {
        return Long.valueOf(request.getHeader("X-Tenant-Id"));
    }
}
