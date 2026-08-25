package com.antshorttv.ai;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai")
public class BuiltInAgentCatalogController {
    private final BuiltInAgentCatalogService service;

    public BuiltInAgentCatalogController(BuiltInAgentCatalogService service) {
        this.service = service;
    }

    @GetMapping("/agents")
    @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<List<BuiltInAgentResponse>> agents(HttpServletRequest request) {
        return ApiResponse.success(service.agents());
    }

    @GetMapping("/agents/{code}")
    @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<BuiltInAgentResponse> agent(@PathVariable String code, HttpServletRequest request) {
        return ApiResponse.success(service.agent(code));
    }

    @PostMapping("/agents/{code}/preview")
    @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<BuiltInAgentPreviewResponse> preview(
        @PathVariable String code,
        @RequestBody BuiltInAgentPreviewRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.preview(code, body == null ? null : body.variables()));
    }

    @GetMapping("/skills")
    @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<List<BuiltInSkillResponse>> skills(HttpServletRequest request) {
        return ApiResponse.success(service.skills());
    }

    @GetMapping("/skills/{code}")
    @RequirePlatformPermission("PLATFORM_AI_AGENT_VIEW")
    public ApiResponse<BuiltInSkillResponse> skill(@PathVariable String code, HttpServletRequest request) {
        return ApiResponse.success(service.skill(code));
    }
}
