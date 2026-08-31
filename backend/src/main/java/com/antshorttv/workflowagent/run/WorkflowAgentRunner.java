package com.antshorttv.workflowagent.run;

import com.antshorttv.ai.AiChatMessage;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextRequest;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.AiToolCall;
import com.antshorttv.ai.AiToolDefinition;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.agent.WorkflowAgentService;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.skill.WorkflowSkillView;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.antshorttv.workflowagent.tool.ToolFailurePolicy;
import com.antshorttv.workflowagent.tool.WorkflowToolDefinition;
import com.antshorttv.workflowagent.tool.WorkflowToolRegistry;
import com.antshorttv.workflowagent.tool.WorkflowToolSchemaValidator;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkflowAgentRunner {
    private static final Set<String> TRUSTED_SCOPE_ARGUMENTS = Set.of(
        "tenantId", "userId", "projectId", "episodeId", "scriptId", "taskId",
        "analysisStageId", "agentRunId", "permissions"
    );

    private final WorkflowAgentService agents;
    private final WorkflowSkillService skills;
    private final WorkflowToolRegistry tools;
    private final WorkflowToolSchemaValidator schemaValidator;
    private final AiInvocationService invocation;
    private final WorkflowAgentRunRepository runs;
    private final WorkflowAgentScopeGuard scopeGuard;
    private final WorkflowAgentProperties properties;
    private final ObjectMapper json;
    private final EpisodeSplittingRunPolicy splitPolicy;

    @Autowired
    public WorkflowAgentRunner(
        WorkflowAgentService agents,
        WorkflowSkillService skills,
        WorkflowToolRegistry tools,
        WorkflowToolSchemaValidator schemaValidator,
        AiInvocationService invocation,
        WorkflowAgentRunRepository runs,
        WorkflowAgentScopeGuard scopeGuard,
        WorkflowAgentProperties properties,
        ObjectMapper json,
        EpisodeSplittingRunPolicy splitPolicy
    ) {
        this.agents = agents;
        this.skills = skills;
        this.tools = tools;
        this.schemaValidator = schemaValidator;
        this.invocation = invocation;
        this.runs = runs;
        this.scopeGuard = scopeGuard;
        this.properties = properties;
        this.json = json;
        this.splitPolicy = splitPolicy;
    }

    public WorkflowAgentRunner(
        WorkflowAgentService agents,
        WorkflowSkillService skills,
        WorkflowToolRegistry tools,
        WorkflowToolSchemaValidator schemaValidator,
        AiInvocationService invocation,
        WorkflowAgentRunRepository runs,
        WorkflowAgentScopeGuard scopeGuard,
        WorkflowAgentProperties properties,
        ObjectMapper json
    ) {
        this(agents, skills, tools, schemaValidator, invocation, runs, scopeGuard,
            properties, json, null);
    }

    public WorkflowAgentRunResult runFormal(WorkflowAgentRunInput input) {
        return runFormal(freezeFormal(input.agentCode()), input);
    }

    public WorkflowAgentExecutionPlan freezeFormal(String agentCode) {
        WorkflowAgentRecord agent = agents.loadForRun(agentCode);
        return new WorkflowAgentExecutionPlan(agent, loadSkills(agent.skillCodes()));
    }

    public WorkflowAgentRunResult runFormal(WorkflowAgentExecutionPlan plan, WorkflowAgentRunInput input) {
        if (!plan.agent().code().equals(input.agentCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "冻结的 Agent 配置与运行 code 不匹配。");
        }
        return execute(plan.agent(), "FORMAL", input, plan.skillSnapshots());
    }

    public WorkflowAgentRunResult runTest(WorkflowAgentCommand temporary, WorkflowAgentRunInput input) {
        agents.validate(temporary, false);
        WorkflowAgentRecord agent = new WorkflowAgentRecord(
            null,
            temporary.code() == null || temporary.code().isBlank() ? "temporary-agent" : temporary.code(),
            temporary.name(), temporary.description(), temporary.systemPrompt(), temporary.modelId(),
            temporary.temperature(), temporary.maxTokens(), temporary.maxSteps(), temporary.status(),
            0L, input.userId(), input.userId(), LocalDateTime.now(), LocalDateTime.now(),
            temporary.skillCodes() == null ? List.of() : List.copyOf(temporary.skillCodes()),
            temporary.toolCodes() == null ? List.of() : List.copyOf(temporary.toolCodes())
        );
        return execute(agent, "TEST", input, null);
    }

    private WorkflowAgentRunResult execute(
        WorkflowAgentRecord agent,
        String runType,
        WorkflowAgentRunInput input,
        List<WorkflowAgentSkillSnapshot> frozenSkills
    ) {
        requireInput(input);
        scopeGuard.requireAuthorized(input, agent.toolCodes());
        List<WorkflowAgentSkillSnapshot> skillSnapshots = frozenSkills == null
            ? loadSkills(agent.skillCodes()) : List.copyOf(frozenSkills);
        List<WorkflowToolDefinition> allowedTools = agent.toolCodes().stream().map(tools::require).toList();
        String prompt = composePrompt(agent, skillSnapshots);
        Long effectiveModelId = input.modelIdOverride() == null ? agent.modelId() : input.modelIdOverride();
        if (input.modelIdOverride() != null) {
            agents.requireToolCallingModel(effectiveModelId);
        }
        Long runId = runs.start(new WorkflowAgentRunStart(
            agent.id(), agent.code(), runType, input.tenantId(), input.userId(), input.projectId(),
            input.episodeId(), input.scriptId(), input.taskId(), input.analysisStageId(), effectiveModelId,
            agent.temperature(), agent.maxTokens(), agent.maxSteps(), prompt, skillSnapshots,
            agent.toolCodes()
        ));
        Instant deadline = Instant.now().plusSeconds(properties.getRunTimeoutSeconds());
        try {
            return runLoop(runId, agent, effectiveModelId, input, prompt, allowedTools, deadline,
                WorkflowAgentRunContract.forAgent(agent.code()));
        } catch (BusinessException exception) {
            runs.fail(runId, exception.getErrorCode().name(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            runs.fail(runId, ErrorCode.WORKFLOW_AGENT_TOOL_INVALID.name(), safeMessage(exception));
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
                "Agent 运行失败：" + safeMessage(exception));
        }
    }

    private WorkflowAgentRunResult runLoop(
        Long runId,
        WorkflowAgentRecord agent,
        Long modelId,
        WorkflowAgentRunInput input,
        String prompt,
        List<WorkflowToolDefinition> allowedTools,
        Instant deadline,
        WorkflowAgentRunContract contract
    ) {
        WorkflowToolRunState runState = new WorkflowToolRunState();
        boolean splitting = EpisodeSplittingRunPolicy.AGENT_CODE.equals(agent.code());
        if (splitting && splitPolicy != null) {
            splitPolicy.preflight(input).ifPresent(reason ->
                runState.beginSplitFallback(reason.name()));
        }
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(prompt));
        messages.add(AiChatMessage.user("CHUNK_FALLBACK".equals(runState.splitMode())
            ? fallbackInstruction(runState.splitFallbackReason()) : input.input()));
        Set<String> allowlist = new HashSet<>(agent.toolCodes());
        List<WorkflowAgentModelCall> modelCalls = new ArrayList<>();
        Set<String> trustedPermissions = input.executionId() == null
            ? Set.of()
            : Set.of("SCRIPT:VIEW", "SCRIPT:EDIT");
        ToolExecutionContext context = new ToolExecutionContext(
            input.tenantId(), input.userId(), input.projectId(), input.episodeId(), input.scriptId(),
            input.taskId(), input.analysisStageId(), runId, input.executionId(), input.attemptId(),
            input.executionVersion(), trustedPermissions, deadline, runState);
        int stepNo = 0;
        String traceId = "workflow-agent-" + UUID.randomUUID();
        for (int modelRound = 1; stepNo < agent.maxSteps(); modelRound++) {
            requireBeforeDeadline(deadline);
            AiInvocationResult<AiTextResponse> result;
            int modelStep = ++stepNo;
            try {
                result = invocation.invokeText(AiInvocationRequest.text()
                    .tenantId(input.tenantId())
                    .userId(input.userId())
                    .projectId(input.projectId())
                    .taskId(input.taskId())
                    .modelId(modelId)
                    .businessSceneCode("workflow_agent")
                    .traceId(traceId)
                    .executionId(input.executionId())
                    .attemptId(input.attemptId())
                    .executionVersion(input.executionVersion())
                    .phase(("CHUNK_FALLBACK".equals(runState.splitMode())
                        ? "AGENT_FALLBACK_STEP_" : "AGENT_STEP_") + modelRound)
                    .idempotencyKey("agent-run-" + runId
                        + ("CHUNK_FALLBACK".equals(runState.splitMode()) ? "-fallback-" : "-model-")
                        + modelRound)
                    .requestSummary("Agent " + agent.code() + " round " + modelRound)
                    .textRequest(new AiTextRequest(
                        null, null, agent.temperature().doubleValue(), agent.maxTokens(), null, false,
                        null, remainingSeconds(deadline), 0,
                        messages, activeProviderTools(allowedTools, splitting, runState),
                        splitting ? "disabled" : null
                    ))
                    .build());
            } catch (AiGatewayException exception) {
                runs.recordFailedModelStep(runId, modelStep, exception.getAiCallLogId(),
                    exception.getErrorCode().name(), exception.getMessage());
                if (splitting && splitPolicy != null) {
                    var fallback = splitPolicy.classifyGateway(exception, runState);
                    if (fallback.isPresent()) {
                        beginFallback(messages, prompt, runState, fallback.get());
                        continue;
                    }
                }
                throw exception;
            }
            AiTextResponse response = result.response();
            modelCalls.add(new WorkflowAgentModelCall(
                result.aiCallLogId(), result.resolvedModelId(), result.providerId(),
                result.providerRequestId(), result.transportOutcome(), result.businessOutcome()));
            List<AiToolCall> calls = response == null ? List.of() : response.toolCalls();
            String finalContent = response == null ? null : response.content();
            runs.recordModelStep(runId, modelStep, result.aiCallLogId(), calls, finalContent);
            if (calls.isEmpty()) {
                if (splitting && splitPolicy != null) {
                    var fallback = splitPolicy.classify(response, runState);
                    if (fallback.isPresent()) {
                        beginFallback(messages, prompt, runState, fallback.get());
                        continue;
                    }
                }
                try {
                    contract.requireComplete(runState);
                } catch (BusinessException error) {
                    if (splitting && "CHUNK_FALLBACK".equals(runState.splitMode())) {
                        messages.add(AiChatMessage.user(
                            "分块候选分析已完成，但流程尚未落库。立即调用 save_episode_splitting，"
                                + "不得输出普通文本结束流程。"));
                        continue;
                    }
                    throw error;
                }
                String output = finalContent == null ? "" : finalContent;
                runs.complete(runId, output);
                return new WorkflowAgentRunResult(runId, output, modelCalls);
            }
            messages.add(AiChatMessage.assistantToolCalls(calls));
            for (AiToolCall call : calls) {
                if (stepNo >= agent.maxSteps()) {
                    throw new BusinessException(ErrorCode.WORKFLOW_AGENT_STEP_LIMIT,
                        "Agent 已达到最大执行步数 " + agent.maxSteps() + "，停止执行后续工具。");
                }
                requireBeforeDeadline(deadline);
                int toolStep = ++stepNo;
                if (!allowlist.contains(call.code())) {
                    BusinessException error = new BusinessException(
                        ErrorCode.WORKFLOW_AGENT_TOOL_UNAUTHORIZED,
                        "模型请求了未授权工具：" + call.code()
                    );
                    runs.recordFailedToolStep(runId, toolStep, call.code(), call.argumentsJson(),
                        error.getErrorCode().name(), error.getMessage());
                    throw error;
                }
                try {
                    contract.requireNext(runState, call.code());
                } catch (BusinessException error) {
                    runs.recordFailedToolStep(runId, toolStep, call.code(), call.argumentsJson(),
                        error.getErrorCode().name(), error.getMessage());
                    if (splitting && "CHUNK_FALLBACK".equals(runState.splitMode())) {
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(error)));
                        continue;
                    }
                    throw error;
                }
                WorkflowToolDefinition definition = tools.require(call.code());
                try {
                    requireBoundedSavePayload(call.code(), call.argumentsJson());
                    JsonNode arguments = parseArguments(call.argumentsJson());
                    rejectTrustedScope(arguments);
                    schemaValidator.validate(definition.inputSchema(), arguments);
                    JsonNode output = definition.executor().execute(context, arguments);
                    requireBeforeDeadline(deadline);
                    schemaValidator.validate(definition.outputSchema(), output);
                    String serialized = json.writeValueAsString(output);
                    runs.recordToolStep(runId, toolStep, call.code(), call.argumentsJson(), serialized);
                    runState.recordSuccess(call.code());
                    if (contract.isTerminal(call.code())) {
                        runs.complete(runId, serialized);
                        return new WorkflowAgentRunResult(runId, serialized, modelCalls);
                    }
                    messages.add(AiChatMessage.toolResult(call.id(), serialized));
                } catch (Exception exception) {
                    BusinessException normalized = normalizeToolFailure(exception);
                    runs.recordFailedToolStep(runId, toolStep, call.code(), call.argumentsJson(),
                        normalized.getErrorCode().name(), normalized.getMessage());
                    if (definition.failurePolicy() == ToolFailurePolicy.RETURN_TO_MODEL
                        || isCorrectableSplitBoundaryFailure(call.code(), normalized)) {
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(normalized)));
                    } else {
                        throw normalized;
                    }
                }
            }
        }
        throw new BusinessException(ErrorCode.WORKFLOW_AGENT_STEP_LIMIT,
            "Agent 已达到最大执行步数 " + agent.maxSteps() + "，仍未产生最终结果。");
    }

    private List<WorkflowAgentSkillSnapshot> loadSkills(List<String> codes) {
        return codes.stream().map(code -> {
            WorkflowSkillView skill = skills.detail(code);
            return new WorkflowAgentSkillSnapshot(skill.code(), skill.name(), skill.revision(), skill.content());
        }).toList();
    }

    private String composePrompt(
        WorkflowAgentRecord agent,
        List<WorkflowAgentSkillSnapshot> skillSnapshots
    ) {
        StringBuilder prompt = new StringBuilder()
            .append("# Workflow Agent\n\n")
            .append("code: ").append(agent.code()).append("\n\n")
            .append(agent.systemPrompt().strip()).append("\n");
        if (!skillSnapshots.isEmpty()) {
            prompt.append("\n# Associated Skills (ordered)\n");
            for (WorkflowAgentSkillSnapshot skill : skillSnapshots) {
                prompt.append("\n## Skill: ").append(skill.code())
                    .append(" (revision ").append(skill.revision()).append(")\n\n")
                    .append(skill.content().strip()).append("\n");
            }
        }
        return prompt.toString();
    }

    private AiToolDefinition providerTool(WorkflowToolDefinition tool) {
        return new AiToolDefinition(tool.code(), tool.description(), json.convertValue(
            tool.inputSchema(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
        ));
    }

    private JsonNode parseArguments(String value) {
        try {
            JsonNode arguments = json.readTree(value == null || value.isBlank() ? "{}" : value);
            if (!arguments.isObject()) {
                throw new IllegalArgumentException("工具参数必须是 JSON object。");
            }
            return arguments;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("工具参数不是合法 JSON。", exception);
        }
    }

    private List<AiToolDefinition> activeProviderTools(
        List<WorkflowToolDefinition> allowedTools,
        boolean splitting,
        WorkflowToolRunState state
    ) {
        if (!splitting) {
            return allowedTools.stream().map(this::providerTool).toList();
        }
        Set<String> activeCodes = "CHUNK_FALLBACK".equals(state.splitMode())
            ? Set.of("read_script_structure", "analyze_script_chunks", "save_episode_splitting")
            : Set.of("read_current_script", "save_episode_splitting");
        return allowedTools.stream()
            .filter(tool -> activeCodes.contains(tool.code()))
            .map(this::providerTool)
            .toList();
    }

    private boolean isCorrectableSplitBoundaryFailure(String toolCode, BusinessException error) {
        return "save_episode_splitting".equals(toolCode)
            && error.getErrorCode() == ErrorCode.VALIDATION_ERROR;
    }

    private void beginFallback(
        List<AiChatMessage> messages,
        String prompt,
        WorkflowToolRunState state,
        EpisodeSplittingRunPolicy.FallbackReason reason
    ) {
        state.beginSplitFallback(reason.name());
        messages.clear();
        messages.add(AiChatMessage.system(prompt));
        messages.add(AiChatMessage.user(fallbackInstruction(reason.name())));
    }

    private String fallbackInstruction(String reason) {
        return "全文边界分析未完成，原因：" + reason
            + "。立即调用 read_script_structure，随后调用 analyze_script_chunks，"
            + "最后仅用可信候选调用 save_episode_splitting。";
    }

    private void requireBoundedSavePayload(String toolCode, String argumentsJson) {
        WorkflowAgentPayloadGuard.requireBounded(
            toolCode, argumentsJson, properties.getMaxLogPayloadBytes());
    }

    private void rejectTrustedScope(JsonNode arguments) {
        TRUSTED_SCOPE_ARGUMENTS.forEach(field -> {
            if (arguments.has(field)) {
                throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
                    "工具参数不得提供服务端作用域字段：" + field);
            }
        });
    }

    private void requireBeforeDeadline(Instant deadline) {
        if (!Instant.now().isBefore(deadline)) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TIMEOUT,
                "Agent 执行超时（" + Duration.ofSeconds(properties.getRunTimeoutSeconds()).toSeconds() + " 秒）。");
        }
    }

    private int remainingSeconds(Instant deadline) {
        long seconds = Duration.between(Instant.now(), deadline).toSeconds();
        return Math.toIntExact(Math.max(1, Math.min(Integer.MAX_VALUE, seconds)));
    }

    private void requireInput(WorkflowAgentRunInput input) {
        if (input == null || input.userId() == null || input.tenantId() == null
            || input.agentCode() == null || input.agentCode().isBlank()
            || input.input() == null || input.input().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Agent 运行参数不完整。");
        }
    }

    private BusinessException normalizeToolFailure(Exception exception) {
        if (exception instanceof BusinessException business) {
            return business;
        }
        return new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
            "工具调用失败：" + safeMessage(exception));
    }

    private String writeError(BusinessException error) {
        try {
            return json.writeValueAsString(Map.of(
                "ok", false,
                "errorCode", error.getErrorCode().name(),
                "message", error.getMessage()
            ));
        } catch (JsonProcessingException exception) {
            return "{\"ok\":false}";
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
