package com.antshorttv.workflowagent.agent;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import com.antshorttv.workflowagent.WorkflowAiPermissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
@RequestMapping("/api/platform/ai/workflow-agents")
public class WorkflowAgentController {
    private final WorkflowAgentService service;

    public WorkflowAgentController(WorkflowAgentService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_VIEW)
    public ApiResponse<List<WorkflowAgentRecord>> list(
        @RequestParam(required = false) String query
    ) {
        return ApiResponse.success(service.list(query));
    }

    @GetMapping("/{code}")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_VIEW)
    public ApiResponse<WorkflowAgentRecord> detail(@PathVariable String code) {
        return ApiResponse.success(service.detail(code));
    }

    @PostMapping
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRecord> create(@Valid @RequestBody CreateAgentRequest body) {
        return ApiResponse.success(service.create(body.toCommand()));
    }

    @PutMapping("/{code}")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRecord> update(
        @PathVariable String code,
        @Valid @RequestBody UpdateAgentRequest body
    ) {
        return ApiResponse.success(service.update(code, body.expectedRevision(), body.toCommand(code)));
    }

    @PostMapping("/{code}/copy")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRecord> copy(
        @PathVariable String code,
        @Valid @RequestBody CopyAgentRequest body
    ) {
        return ApiResponse.success(service.copy(code, body.targetCode()));
    }

    @PostMapping("/{code}/enable")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRecord> enable(@PathVariable String code) {
        return ApiResponse.success(service.enable(code));
    }

    @PostMapping("/{code}/disable")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRecord> disable(@PathVariable String code) {
        return ApiResponse.success(service.disable(code));
    }

    @DeleteMapping("/{code}")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ApiResponse.ok();
    }

    public record CreateAgentRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotBlank String systemPrompt,
        @NotNull Long modelId,
        @NotNull BigDecimal temperature,
        @NotNull Integer maxTokens,
        @NotNull Integer maxSteps,
        @NotBlank String status,
        List<String> skillCodes,
        List<String> toolCodes
    ) {
        WorkflowAgentCommand toCommand() {
            return new WorkflowAgentCommand(code, name, description, systemPrompt, modelId,
                temperature, maxTokens, maxSteps, status, skillCodes, toolCodes);
        }
    }

    public record UpdateAgentRequest(
        @NotBlank String name,
        String description,
        @NotBlank String systemPrompt,
        @NotNull Long modelId,
        @NotNull BigDecimal temperature,
        @NotNull Integer maxTokens,
        @NotNull Integer maxSteps,
        @NotBlank String status,
        List<String> skillCodes,
        List<String> toolCodes,
        @NotNull Long expectedRevision
    ) {
        WorkflowAgentCommand toCommand(String code) {
            return new WorkflowAgentCommand(code, name, description, systemPrompt, modelId,
                temperature, maxTokens, maxSteps, status, skillCodes, toolCodes);
        }
    }

    public record CopyAgentRequest(@NotBlank String targetCode) {
    }
}
