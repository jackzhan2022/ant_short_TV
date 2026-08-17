package com.antshorttv.video;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class AiVideoTaskController {
    private final AiVideoTaskService aiVideoTaskService;

    public AiVideoTaskController(AiVideoTaskService aiVideoTaskService) {
        this.aiVideoTaskService = aiVideoTaskService;
    }

    @GetMapping("/ai-video-tasks")
    @RequirePermission("AI_VIDEO_TASK:VIEW")
    public ApiResponse<List<AiVideoTaskResponse>> list(
        @PathVariable Long projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storyboardId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.list(tenantId(request), projectId, status, storyboardId));
    }

    @PostMapping("/ai-video-tasks")
    @RequirePermission("AI_VIDEO_TASK:CREATE")
    public ApiResponse<AiVideoTaskResponse> create(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateAiVideoTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.create(tenantId(request), projectId, body, request));
    }

    @GetMapping("/ai-video-tasks/{taskId}")
    @RequirePermission("AI_VIDEO_TASK:VIEW")
    public ApiResponse<AiVideoTaskResponse> detail(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.detail(tenantId(request), projectId, taskId));
    }

    @PostMapping("/ai-video-tasks/{taskId}/poll")
    @RequirePermission("AI_VIDEO_TASK:VIEW")
    public ApiResponse<AiVideoTaskResponse> poll(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.poll(tenantId(request), projectId, taskId, request));
    }

    @PostMapping("/ai-video-tasks/{taskId}/cancel")
    @RequirePermission("AI_VIDEO_TASK:CANCEL")
    public ApiResponse<AiVideoTaskResponse> cancel(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.cancel(tenantId(request), projectId, taskId, request));
    }

    @PostMapping("/ai-video-tasks/{taskId}/regenerate")
    @RequirePermission("AI_VIDEO_TASK:CREATE")
    public ApiResponse<AiVideoTaskResponse> regenerate(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.regenerate(tenantId(request), projectId, taskId, request));
    }

    @DeleteMapping("/ai-video-tasks/{taskId}")
    @RequirePermission("AI_VIDEO_TASK:DELETE")
    public ApiResponse<Void> deleteTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        aiVideoTaskService.deleteTask(tenantId(request), projectId, taskId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/ai-video-tasks/{taskId}/results")
    @RequirePermission("AI_VIDEO_TASK:VIEW")
    public ApiResponse<List<AiVideoResultResponse>> results(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.results(tenantId(request), projectId, taskId));
    }

    @GetMapping("/ai-video-results/{resultId}/download")
    @RequirePermission("AI_VIDEO_RESULT:DOWNLOAD")
    public ApiResponse<AiVideoResultResponse> download(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.download(tenantId(request), projectId, resultId, request));
    }

    @PostMapping("/ai-video-results/{resultId}/save-material")
    @RequirePermission("AI_VIDEO_RESULT:SAVE")
    public ApiResponse<AiVideoResultResponse> saveMaterial(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.saveMaterial(tenantId(request), projectId, resultId, request));
    }

    @PostMapping("/ai-video-results/{resultId}/bind-storyboard")
    @RequirePermission("AI_VIDEO_RESULT:BIND")
    public ApiResponse<AiVideoResultResponse> bindStoryboard(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(aiVideoTaskService.bindStoryboard(tenantId(request), projectId, resultId, request));
    }

    @DeleteMapping("/ai-video-results/{resultId}")
    @RequirePermission("AI_VIDEO_TASK:DELETE")
    public ApiResponse<Void> deleteResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        aiVideoTaskService.deleteResult(tenantId(request), projectId, resultId, request);
        return ApiResponse.ok();
    }

    private Long tenantId(HttpServletRequest request) {
        return Long.valueOf(request.getHeader("X-Tenant-Id"));
    }
}
