package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.RequireProjectPermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
class StoryboardEditorController {
    private final StoryboardEditorQueryService editor;
    StoryboardEditorController(StoryboardEditorQueryService editor) { this.editor = editor; }

    @GetMapping("/storyboard-editor-page")
    @RequireProjectPermission("PROJECT:VIEW")
    public ApiResponse<StoryboardWorkspacePageResponse> page(@PathVariable Long projectId,
        @RequestParam(required=false) Integer episodeNo, @RequestParam(required=false) Integer current,
        @RequestParam(required=false) Integer pageSize, @RequestParam(required=false) Long storyboardId,
        HttpServletRequest request) {
        return ApiResponse.success(editor.page(TenantRequestSupport.tenantId(request), projectId,
            episodeNo, current, pageSize, storyboardId));
    }

    @GetMapping("/storyboard-episodes/{episodeId}/details")
    @RequireProjectPermission("PROJECT:VIEW")
    public ApiResponse<ScriptEpisodeResponse> details(@PathVariable Long projectId, @PathVariable Long episodeId,
        HttpServletRequest request) {
        return ApiResponse.success(editor.episodeDetails(TenantRequestSupport.tenantId(request), projectId, episodeId));
    }
}
