package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import com.antshorttv.workflowagent.WorkflowAiPermissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
@RequestMapping("/api/platform/ai/workflow-skills")
public class WorkflowSkillController {
    private final WorkflowSkillService service;

    public WorkflowSkillController(WorkflowSkillService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePlatformPermission(WorkflowAiPermissions.SKILL_VIEW)
    public ApiResponse<List<WorkflowSkillView>> list(
        @RequestParam(required = false) String query
    ) {
        return ApiResponse.success(service.list(query));
    }

    @GetMapping("/{code}")
    @RequirePlatformPermission(WorkflowAiPermissions.SKILL_VIEW)
    public ApiResponse<WorkflowSkillView> detail(@PathVariable String code) {
        return ApiResponse.success(service.detail(code));
    }

    @PostMapping
    @RequirePlatformPermission(WorkflowAiPermissions.SKILL_EDIT)
    public ApiResponse<WorkflowSkillView> create(@Valid @RequestBody CreateSkillRequest body) {
        return ApiResponse.success(service.create(body.code(), body.content()));
    }

    @PutMapping("/{code}")
    @RequirePlatformPermission(WorkflowAiPermissions.SKILL_EDIT)
    public ApiResponse<WorkflowSkillView> update(
        @PathVariable String code,
        @Valid @RequestBody UpdateSkillRequest body
    ) {
        return ApiResponse.success(service.update(code, body.content(), body.expectedRevision()));
    }

    @PostMapping("/{code}/copy")
    @RequirePlatformPermission(WorkflowAiPermissions.SKILL_EDIT)
    public ApiResponse<WorkflowSkillView> copy(
        @PathVariable String code,
        @Valid @RequestBody CopySkillRequest body
    ) {
        return ApiResponse.success(service.copy(code, body.targetCode()));
    }

    @DeleteMapping("/{code}")
    @RequirePlatformPermission(WorkflowAiPermissions.SKILL_EDIT)
    public ApiResponse<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ApiResponse.ok();
    }

    public record CreateSkillRequest(
        @NotBlank String code,
        @NotBlank String content
    ) {
    }

    public record UpdateSkillRequest(
        @NotBlank String content,
        @NotBlank String expectedRevision
    ) {
    }

    public record CopySkillRequest(@NotBlank String targetCode) {
    }
}
