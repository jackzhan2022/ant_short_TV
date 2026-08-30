package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import com.antshorttv.workflowagent.WorkflowAiPermissions;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai/agent-tools")
public class WorkflowToolCatalogController {
    private final WorkflowToolRegistry registry;

    public WorkflowToolCatalogController(WorkflowToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_VIEW)
    public ApiResponse<List<WorkflowToolMetadata>> catalog() {
        return ApiResponse.success(registry.catalog());
    }
}
