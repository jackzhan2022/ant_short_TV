package com.antshorttv.video;

import com.antshorttv.common.*;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class SubtitleEraseController {
    private final SubtitleEraseTaskService service;
    public SubtitleEraseController(SubtitleEraseTaskService service){this.service=service;}
    @PostMapping("/ai-video-results/{resultId}/subtitle-erasure")
    @RequireProjectPermission({"AI_VIDEO_TASK:VIEW","AI_VIDEO_TASK:CREATE","AI_SERVICE:USE"})
    public ApiResponse<SubtitleEraseTaskService.Response> create(@PathVariable long projectId,@PathVariable long resultId,
        @RequestBody SubtitleEraseRequest body,HttpServletRequest request){
        return ApiResponse.success(service.create(TenantRequestSupport.tenantId(request),projectId,resultId,body,request.getHeader("Idempotency-Key")));
    }
    @GetMapping("/subtitle-erase-tasks")
    @RequireProjectPermission("AI_VIDEO_TASK:VIEW")
    public ApiResponse<List<SubtitleEraseTaskService.Response>> list(@PathVariable long projectId,@RequestParam List<Long> storyboardIds,HttpServletRequest request){
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request),projectId,storyboardIds));
    }
    @GetMapping("/subtitle-erase-tasks/{id}")
    @RequireProjectPermission("AI_VIDEO_TASK:VIEW")
    public ApiResponse<SubtitleEraseTaskService.Response> detail(@PathVariable long projectId,@PathVariable long id,HttpServletRequest request){
        return ApiResponse.success(service.detail(TenantRequestSupport.tenantId(request),projectId,id));
    }
    @PostMapping("/subtitle-erase-tasks/{id}/retry")
    @RequireProjectPermission({"AI_VIDEO_TASK:VIEW","AI_VIDEO_TASK:CREATE","AI_SERVICE:USE"})
    public ApiResponse<SubtitleEraseTaskService.Response> retry(@PathVariable long projectId,@PathVariable long id,HttpServletRequest request){
        return ApiResponse.success(service.retry(TenantRequestSupport.tenantId(request),projectId,id,request.getHeader("Idempotency-Key")));
    }
}
