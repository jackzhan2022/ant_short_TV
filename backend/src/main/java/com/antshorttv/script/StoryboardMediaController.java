package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/storyboard-media")
public class StoryboardMediaController {
    private final StoryboardMediaQueryService media;

    public StoryboardMediaController(StoryboardMediaQueryService media) {
        this.media = media;
    }

    @GetMapping
    public ApiResponse<Map<String, List<Map<String, Object>>>> summary(
        @PathVariable Long projectId, @RequestParam List<Long> storyboardIds, HttpServletRequest request
    ) {
        return ApiResponse.success(media.summary(TenantRequestSupport.tenantId(request), projectId, storyboardIds));
    }
}
