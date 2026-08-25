package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ScriptWorkflowController {

    private final ScriptWorkflowService scriptWorkflowService;

    public ScriptWorkflowController(ScriptWorkflowService scriptWorkflowService) {
        this.scriptWorkflowService = scriptWorkflowService;
    }

    @GetMapping("/script-workspace")
    @RequireProjectPermission("PROJECT:VIEW")
    public ApiResponse<ScriptWorkspaceResponse> workspace(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.workspace(tenantId(request), projectId));
    }

    @GetMapping("/script-analysis/current")
    @RequireProjectPermission("PROJECT:VIEW")
    public ApiResponse<ScriptAnalysisTaskResponse> currentAnalysis(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.currentAnalysis(tenantId(request), projectId));
    }

    @PostMapping("/script-analysis/current/retry/{stageCode}")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> retryAnalysis(
        @PathVariable Long projectId,
        @PathVariable String stageCode,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.retryAnalysis(tenantId(request), projectId, stageCode));
    }

    @PostMapping("/script-analysis/current/reanalyze")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> reanalyze(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.reanalyze(tenantId(request), projectId));
    }

    @PostMapping("/script-analysis/versions/{versionId}/reanalyze")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> reanalyzeVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.reanalyzeVersion(tenantId(request), projectId, versionId));
    }

    @PostMapping("/scripts/ai-generate")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generate(
        @PathVariable Long projectId,
        @Valid @RequestBody GenerateScriptRequest body,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.submitGenerate(tenantId(request), projectId, body, request));
    }

    @PostMapping("/scripts/ai-rewrite")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> rewrite(
        @PathVariable Long projectId,
        @Valid @RequestBody RewriteScriptRequest body,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.submitRewrite(tenantId(request), projectId, body, request));
    }

    @PutMapping("/scripts/current")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> saveCurrent(
        @PathVariable Long projectId,
        @Valid @RequestBody SaveScriptRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.saveCurrent(tenantId(request), projectId, body, request));
    }

    @PutMapping("/scripts/versions/{versionId}/apply")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> applyVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.applyVersion(tenantId(request), projectId, versionId, request));
    }

    @PostMapping("/scripts/ai-extract-elements")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> extractElements(
        @PathVariable Long projectId,
        @Valid @RequestBody ExtractScriptElementsRequest body,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.submitExtractElements(tenantId(request), projectId, body, request));
    }

    @PutMapping("/script-elements/{elementType}/{elementId}")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> updateElement(
        @PathVariable Long projectId,
        @PathVariable String elementType,
        @PathVariable Long elementId,
        @Valid @RequestBody UpdateScriptElementRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.updateElement(tenantId(request), projectId, elementType, elementId, body, request));
    }

    @PutMapping("/script-elements/{elementType}/{elementId}/confirm")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> confirmElement(
        @PathVariable Long projectId,
        @PathVariable String elementType,
        @PathVariable Long elementId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.confirmElement(tenantId(request), projectId, elementType, elementId, request));
    }

    @DeleteMapping("/script-elements/{elementType}/{elementId}")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> deleteElement(
        @PathVariable Long projectId,
        @PathVariable String elementType,
        @PathVariable Long elementId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.deleteElement(tenantId(request), projectId, elementType, elementId, request));
    }

    @PostMapping("/storyboards/ai-breakdown")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> breakdownStoryboards(
        @PathVariable Long projectId,
        @Valid @RequestBody StoryboardBreakdownRequest body,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.submitStoryboardBreakdown(tenantId(request), projectId, body, request));
    }

    @PostMapping("/storyboards")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> createStoryboard(
        @PathVariable Long projectId,
        @Valid @RequestBody SaveStoryboardRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.createStoryboard(tenantId(request), projectId, body, request));
    }

    @PutMapping("/storyboards/{storyboardId}")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> updateStoryboard(
        @PathVariable Long projectId,
        @PathVariable Long storyboardId,
        @Valid @RequestBody SaveStoryboardRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.updateStoryboard(tenantId(request), projectId, storyboardId, body, request));
    }

    @PutMapping("/storyboards/{storyboardId}/move")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> moveStoryboard(
        @PathVariable Long projectId,
        @PathVariable Long storyboardId,
        @Valid @RequestBody MoveStoryboardRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.moveStoryboard(tenantId(request), projectId, storyboardId, body, request));
    }

    @PutMapping("/storyboards/confirm")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> confirmStoryboards(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.confirmStoryboards(tenantId(request), projectId, request));
    }

    @DeleteMapping("/storyboards/{storyboardId}")
    @RequireProjectPermission("SCRIPT:EDIT")
    public ApiResponse<ScriptWorkspaceResponse> deleteStoryboard(
        @PathVariable Long projectId,
        @PathVariable Long storyboardId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(scriptWorkflowService.deleteStoryboard(tenantId(request), projectId, storyboardId, request));
    }

    @PostMapping("/prompts/ai-generate")
    @RequireProjectPermission("AI_SERVICE:USE")
    public ResponseEntity<ApiResponse<AiExecutionResponse>> generatePrompts(
        @PathVariable Long projectId,
        @Valid @RequestBody GeneratePromptRequest body,
        HttpServletRequest request
    ) {
        return accepted(scriptWorkflowService.submitPromptGeneration(tenantId(request), projectId, body, request));
    }

    private ResponseEntity<ApiResponse<AiExecutionResponse>> accepted(AiExecutionResponse execution) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(execution));
    }

    private Long tenantId(HttpServletRequest request) {
        return TenantRequestSupport.tenantId(request);
    }
}
