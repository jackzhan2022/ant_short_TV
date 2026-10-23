package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@com.antshorttv.rbac.RequirePermission("MENU:AI_MANAGEMENT")
@RestController
@RequestMapping("/api/tenants/{tenantId}/workflow-skills")
public class TenantWorkflowSkillController {
    private final TenantWorkflowSkillService service;

    public TenantWorkflowSkillController(TenantWorkflowSkillService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<WorkflowSkillView>> list(@PathVariable Long tenantId,
        @RequestParam(required = false) String query, @RequestParam(required = false) String agentCode,
        @RequestParam(required = false) String scope) {
        return ApiResponse.success(service.list(tenantId, query, agentCode, scope));
    }

    @GetMapping("/{code}")
    public ApiResponse<WorkflowSkillView> detail(@PathVariable Long tenantId, @PathVariable String code) {
        return ApiResponse.success(service.detail(tenantId, code));
    }

    @GetMapping("/{code}/template")
    public ApiResponse<WorkflowSkillView> template(@PathVariable Long tenantId, @PathVariable String code) {
        return ApiResponse.success(service.template(tenantId, code));
    }

    @PutMapping("/{code}")
    public ApiResponse<WorkflowSkillView> save(@PathVariable Long tenantId, @PathVariable String code,
        @Valid @RequestBody WorkflowSkillController.UpdateSkillRequest body) {
        return ApiResponse.success(service.save(tenantId, code, body.content(), body.expectedRevision()));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> restore(@PathVariable Long tenantId, @PathVariable String code,
        @Valid @RequestBody RestoreRequest body) {
        service.restore(tenantId, code, body.expectedRevision());
        return ApiResponse.success(null);
    }

    public record RestoreRequest(@NotBlank String expectedRevision) { }
}
