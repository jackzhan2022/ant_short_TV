package com.antshorttv.aiimage;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class AiImageTaskController {
    private final AiImageTaskService aiImageTaskService;

    public AiImageTaskController(AiImageTaskService aiImageTaskService) {
        this.aiImageTaskService = aiImageTaskService;
    }

    @GetMapping("/ai-image-tasks")
    @RequireProjectPermission("AI_IMAGE_TASK:VIEW")
    public ApiResponse<List<AiImageTaskResponse>> list(
        @PathVariable Long projectId,
        @RequestParam(required = false) String taskType,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.list(tenantId(request), projectId, taskType, status));
    }

    @PostMapping("/ai-image-tasks")
    @RequireProjectPermission("AI_IMAGE_TASK:CREATE")
    public ApiResponse<AiImageTaskResponse> create(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateAiImageTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.create(tenantId(request), projectId, body, request));
    }

    @GetMapping("/ai-image-tasks/{taskId}")
    @RequireProjectPermission("AI_IMAGE_TASK:VIEW")
    public ApiResponse<AiImageTaskResponse> detail(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.detail(tenantId(request), projectId, taskId));
    }

    @PostMapping("/ai-image-tasks/{taskId}/regenerate")
    @RequireProjectPermission("AI_IMAGE_TASK:CREATE")
    public ApiResponse<AiImageTaskResponse> regenerate(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.regenerate(tenantId(request), projectId, taskId, request));
    }

    @PutMapping("/ai-image-tasks/{taskId}/cancel")
    @RequireProjectPermission("AI_IMAGE_TASK:CANCEL")
    public ApiResponse<AiImageTaskResponse> cancel(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.cancel(tenantId(request), projectId, taskId, request));
    }

    @DeleteMapping("/ai-image-tasks/{taskId}")
    @RequireProjectPermission("AI_IMAGE_TASK:DELETE")
    public ApiResponse<Void> delete(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        aiImageTaskService.delete(tenantId(request), projectId, taskId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/ai-image-results/{resultId}/download")
    @RequireProjectPermission("AI_IMAGE_TASK:VIEW")
    public ResponseEntity<Resource> downloadResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        Resource resource = aiImageTaskService.download(tenantId(request), projectId, resultId);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("ai-image-result-%d.png".formatted(resultId))
                .build()
                .toString())
            .body(resource);
    }

    @PostMapping("/ai-image-results/{resultId}/save-material")
    @RequireProjectPermission("AI_IMAGE_RESULT:SAVE")
    public ApiResponse<AiImageResultResponse> saveMaterial(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.saveMaterial(tenantId(request), projectId, resultId, request));
    }

    @PutMapping("/ai-image-results/{resultId}/selected")
    @RequireProjectPermission("AI_IMAGE_RESULT:BIND")
    public ApiResponse<AiImageResultResponse> selectResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiImageTaskService.selectResult(tenantId(request), projectId, resultId, request));
    }

    @DeleteMapping("/ai-image-results/{resultId}")
    @RequireProjectPermission("AI_IMAGE_TASK:DELETE")
    public ApiResponse<Void> deleteResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        @RequestParam(defaultValue = "false") boolean force,
        HttpServletRequest request
    ) {
        aiImageTaskService.deleteResult(tenantId(request), projectId, resultId, force, request);
        return ApiResponse.ok();
    }

    private Long tenantId(HttpServletRequest request) {
        return TenantRequestSupport.tenantId(request);
    }
}
