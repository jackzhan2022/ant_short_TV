package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssetExtractionSkillController {
    private final AssetExtractionSkillService skills;
    private final TenantContextResolver contexts;

    public AssetExtractionSkillController(AssetExtractionSkillService skills, TenantContextResolver contexts) {
        this.skills = skills;
        this.contexts = contexts;
    }

    @GetMapping("/api/projects/asset-extraction-skills")
    public ApiResponse<List<AssetExtractionSkillService.Option>> list(HttpServletRequest request) {
        contexts.requireActiveMember(TenantRequestSupport.tenantId(request));
        return ApiResponse.success(skills.list());
    }
}
