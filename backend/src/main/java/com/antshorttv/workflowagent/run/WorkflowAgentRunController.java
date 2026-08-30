package com.antshorttv.workflowagent.run;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import com.antshorttv.security.RequestTenantContextResolver;
import com.antshorttv.workflowagent.WorkflowAiPermissions;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai/workflow-agent-runs")
public class WorkflowAgentRunController {
    private final WorkflowAgentRunner runner;
    private final WorkflowAgentRunRepository runs;
    private final RequestTenantContextResolver tenantContextResolver;

    public WorkflowAgentRunController(
        WorkflowAgentRunner runner,
        WorkflowAgentRunRepository runs,
        RequestTenantContextResolver tenantContextResolver
    ) {
        this.runner = runner;
        this.runs = runs;
        this.tenantContextResolver = tenantContextResolver;
    }

    @PostMapping
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRunResult> runFormal(
        @Valid @RequestBody FormalRunRequest body,
        HttpServletRequest request
    ) {
        var tenant = tenantContextResolver.require(request);
        return ApiResponse.success(runner.runFormal(body.toInput(tenant.tenantId(), tenant.userId())));
    }

    @PostMapping("/test")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_EDIT)
    public ApiResponse<WorkflowAgentRunResult> runTest(
        @Valid @RequestBody TestRunRequest body,
        HttpServletRequest request
    ) {
        var tenant = tenantContextResolver.require(request);
        return ApiResponse.success(runner.runTest(
            body.toCommand(), body.toInput(tenant.tenantId(), tenant.userId())));
    }

    @GetMapping
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_VIEW)
    public ApiResponse<List<WorkflowAgentRunSummary>> list(
        @RequestParam(required = false) String agentCode,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.success(runs.list(agentCode, limit));
    }

    @GetMapping("/{runId}")
    @RequirePlatformPermission(WorkflowAiPermissions.AGENT_VIEW)
    public ApiResponse<WorkflowAgentRunDetail> detail(@PathVariable Long runId) {
        return ApiResponse.success(runs.detail(runId));
    }

    public record FormalRunRequest(
        @NotBlank String agentCode,
        @NotBlank String input,
        Long projectId,
        Long episodeId,
        Long taskId
    ) {
        WorkflowAgentRunInput toInput(Long tenantId, Long userId) {
            return new WorkflowAgentRunInput(agentCode, input, tenantId, projectId, episodeId, taskId, userId);
        }
    }

    public record TestRunRequest(
        String code,
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
        @NotBlank String input,
        Long projectId,
        Long episodeId,
        Long taskId
    ) {
        WorkflowAgentCommand toCommand() {
            return new WorkflowAgentCommand(code, name, description, systemPrompt, modelId, temperature,
                maxTokens, maxSteps, status, skillCodes, toolCodes);
        }

        WorkflowAgentRunInput toInput(Long tenantId, Long userId) {
            return new WorkflowAgentRunInput(
                code == null || code.isBlank() ? "temporary-agent" : code,
                input, tenantId, projectId, episodeId, taskId, userId
            );
        }
    }
}
