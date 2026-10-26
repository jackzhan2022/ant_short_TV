package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/storyboards/{storyboardId}/video-generation-settings")
public class StoryboardVideoSettingsController {
    private final StoryboardVideoSettingsService service;
    public StoryboardVideoSettingsController(StoryboardVideoSettingsService service) { this.service=service; }
    @PutMapping
    public ApiResponse<StoryboardVideoSettingsService.Settings> save(@PathVariable Long projectId,@PathVariable Long storyboardId,
        @Valid @RequestBody StoryboardVideoSettingsService.Settings settings,HttpServletRequest request) {
        return ApiResponse.success(service.save(TenantRequestSupport.tenantId(request),projectId,storyboardId,settings));
    }
    @GetMapping
    public ApiResponse<StoryboardVideoSettingsService.Settings> get(@PathVariable Long projectId,@PathVariable Long storyboardId,HttpServletRequest request) {
        return ApiResponse.success(service.get(TenantRequestSupport.tenantId(request),projectId,storyboardId));
    }
}
