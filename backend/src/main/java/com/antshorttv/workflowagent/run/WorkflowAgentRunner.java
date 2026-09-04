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
import com.antshorttv.workflowagent.tool.EpisodeAssetsPayloadNormalizer;
import com.antshorttv.workflowagent.tool.ToolFailurePolicy;
import com.antshorttv.workflowagent.tool.WorkflowToolDefinition;
import com.antshorttv.workflowagent.tool.WorkflowToolRegistry;
import com.antshorttv.workflowagent.tool.WorkflowToolSchemaValidator;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import com.antshorttv.workflowagent.tool.WorkflowToolValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        scopeGuard.requireExecutionActive(input);
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
                WorkflowAgentRunContract.forAgent(agent.code(),
                    input.reviewScope() == null ? null : input.reviewScope().phase()));
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
            input.executionVersion(), trustedPermissions, deadline, runState, input.reviewScope());
        int stepNo = 0;
        if ("short-drama-storyboard".equals(agent.code())) {
            stepNo = prepareStoryboardContext(runId, agent, input, contract, context, messages,
                allowlist, deadline, stepNo);
        }
        boolean reviewTruncationRecovery = false;
        boolean reviewEvidenceRefreshPending = false;
        boolean reviewEvidenceRefreshUsed = false;
        boolean assetSaveCorrectionUsed = false;
        String storyboardValidationCode = null;
        String traceId = "workflow-agent-" + UUID.randomUUID();
        for (int modelRound = 1; stepNo < agent.maxSteps(); modelRound++) {
            requireBeforeDeadline(deadline);
            scopeGuard.requireExecutionActive(input);
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
                        null, remainingSeconds(deadline),
                        "short-drama-asset-recognition".equals(agent.code()) ? 1 : 0,
                        messages, activeProviderTools(allowedTools, splitting, runState,
                            agent.code(), contract, reviewTruncationRecovery, reviewEvidenceRefreshPending),
                        disableThinking(agent.code(), splitting) ? "disabled" : null
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
            if ("script-review".equals(agent.code()) && isTruncated(response)) {
                reviewTruncationRecovery = true;
                List<String> remainingTools = remainingContractTools(contract, runState);
                messages.add(AiChatMessage.user(
                    "模型输出已截断，审核契约尚未完成。已经成功的读取工具不得重复调用；"
                        + "现在只允许按顺序调用这些尚未完成的工具："
                        + String.join(" -> ", remainingTools) + "。不得输出普通文本。"
                        + (remainingTools.size() == 1 && contract.isTerminal(remainingTools.get(0))
                            ? "直接调用保存工具；候选最多 20 个，每条只保留必要证据与命中，字段务必简洁。"
                            : "完成剩余可信读取后立即调用保存工具。")));
                continue;
            }
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
                scopeGuard.requireExecutionActive(input);
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
                WorkflowToolDefinition definition = tools.require(call.code());
                try {
                    contract.requireNext(runState, call.code());
                } catch (BusinessException error) {
                    runs.recordFailedToolStep(runId, toolStep, call.code(), call.argumentsJson(),
                        error.getErrorCode().name(), error.getMessage());
                    if (definition.failurePolicy() == ToolFailurePolicy.RETURN_TO_MODEL
                        || (splitting && "CHUNK_FALLBACK".equals(runState.splitMode()))) {
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(error)));
                        continue;
                    }
                    throw error;
                }
                try {
                    requireBoundedSavePayload(call.code(), call.argumentsJson());
                    JsonNode arguments = parseArguments(call.argumentsJson());
                    if ("save_episode_assets".equals(call.code())) {
                        arguments = EpisodeAssetsPayloadNormalizer.normalize(arguments);
                    }
                    rejectTrustedScope(arguments);
                    schemaValidator.validate(definition.inputSchema(), arguments);
                    JsonNode output = definition.executor().execute(context, arguments);
                    requireBeforeDeadline(deadline);
                    scopeGuard.requireExecutionActive(input);
                    schemaValidator.validate(definition.outputSchema(), output);
                    String serialized = json.writeValueAsString(output);
                    runs.recordToolStep(runId, toolStep, call.code(), call.argumentsJson(), serialized);
                    runState.recordSuccess(call.code());
                    messages.add(AiChatMessage.toolResult(call.id(), serialized));
                    if (reviewEvidenceRefreshPending && "read_review_content".equals(call.code())) {
                        reviewEvidenceRefreshPending = false;
                        messages.add(AiChatMessage.user(
                            "已重新读取当前审核单元正文。现在只允许调用 save_review_unit_result；"
                                + "候选最多 20 个，删除不能逐字验证的候选，字段务必精简。"));
                    }
                    if (contract.isTerminal(call.code())) {
                        runs.complete(runId, serialized);
                        return new WorkflowAgentRunResult(runId, serialized, modelCalls);
                    }
                } catch (Exception exception) {
                    BusinessException normalized = normalizeToolFailure(exception);
                    runs.recordFailedToolStep(runId, toolStep, call.code(), call.argumentsJson(),
                        normalized.getErrorCode().name(), normalized.getMessage());
                    if ("save_episode_assets".equals(call.code())) {
                        if (assetSaveCorrectionUsed) throw normalized;
                        assetSaveCorrectionUsed = true;
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(normalized)));
                        messages.add(AiChatMessage.user(
                            "保存失败。仅修正错误中指出的字段并再次调用 save_episode_assets；"
                                + "五个数组必须始终存在，证据必须逐字来自当前剧集，不得编造。"));
                        break;
                    }
                    if ("save_episode_storyboards".equals(call.code())) {
                        String validationCode = storyboardValidationCode(normalized);
                        if (storyboardValidationCode != null) throw normalized;
                        storyboardValidationCode = validationCode;
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(normalized)));
                        messages.add(AiChatMessage.user(
                            "分镜保存校验失败。只根据结构化诊断修正对应片段范围或声音归属，"
                                + "然后再次调用 save_episode_storyboards；不得重新读取或重做其他流程。"
                                + "再次提交前必须同时自检：sourceFrom/sourceTo 位于每个分镜对象内部而非根对象；"
                                + "soundSegmentIds 只能包含 DIALOGUE、NARRATION 或 INNER_OS，"
                                + "必须排除 ACTION、METADATA、角色提示行和字幕行。"));
                        break;
                    }
                    if (reviewTruncationRecovery && isReviewEvidenceValidationFailure(call.code(), normalized)) {
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(normalized)));
                        if (!reviewEvidenceRefreshUsed) {
                            reviewEvidenceRefreshUsed = true;
                            reviewEvidenceRefreshPending = true;
                            messages.add(AiChatMessage.user(
                                "保存候选因证据无法验证被拒绝。现在只允许调用一次 read_review_content，"
                                    + "重新读取当前审核单元的可信正文与位置锚点；不得读取整本剧本，"
                                    + "不得调用其他工具。随后只允许调用 save_review_unit_result，"
                                    + "候选最多 20 个，并删除不能逐字验证的候选。"));
                        } else {
                            messages.add(AiChatMessage.user(
                                "证据仍无法验证。不得再次读取正文；删除所有不能逐字验证的候选，"
                                    + "可保存空 candidates。现在只允许调用 save_review_unit_result。"));
                        }
                        break;
                    }
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

    private int prepareStoryboardContext(
        Long runId,
        WorkflowAgentRecord agent,
        WorkflowAgentRunInput input,
        WorkflowAgentRunContract contract,
        ToolExecutionContext context,
        List<AiChatMessage> messages,
        Set<String> allowlist,
        Instant deadline,
        int stepNo
    ) {
        ObjectNode prepared = json.createObjectNode();
        for (String toolCode : contract.preparationToolCodes()) {
            if (stepNo >= agent.maxSteps()) {
                throw new BusinessException(ErrorCode.WORKFLOW_AGENT_STEP_LIMIT,
                    "Agent 已达到最大执行步数 " + agent.maxSteps() + "，无法完成分镜上下文读取。");
            }
            requireBeforeDeadline(deadline);
            scopeGuard.requireExecutionActive(input);
            int toolStep = ++stepNo;
            if (!allowlist.contains(toolCode)) {
                BusinessException error = new BusinessException(
                    ErrorCode.WORKFLOW_AGENT_TOOL_UNAUTHORIZED, "分镜准备工具未获授权：" + toolCode);
                runs.recordFailedToolStep(runId, toolStep, toolCode, "{}",
                    error.getErrorCode().name(), error.getMessage());
                throw error;
            }
            WorkflowToolDefinition definition = tools.require(toolCode);
            try {
                contract.requireNext(context.runState(), toolCode);
                JsonNode arguments = json.createObjectNode();
                schemaValidator.validate(definition.inputSchema(), arguments);
                JsonNode output = definition.executor().execute(context, arguments);
                requireBeforeDeadline(deadline);
                scopeGuard.requireExecutionActive(input);
                schemaValidator.validate(definition.outputSchema(), output);
                String serialized = json.writeValueAsString(output);
                runs.recordToolStep(runId, toolStep, toolCode, "{}", serialized);
                context.runState().recordSuccess(toolCode);
                prepared.set(toolCode, output.deepCopy());
            } catch (Exception exception) {
                BusinessException normalized = normalizeToolFailure(exception);
                runs.recordFailedToolStep(runId, toolStep, toolCode, "{}",
                    normalized.getErrorCode().name(), normalized.getMessage());
                throw normalized;
            }
        }
        messages.add(AiChatMessage.user(
            "以下是服务端已按可信作用域读取并审计的完整分镜规划上下文。"
                + "不要再次读取，也不要引用旧分镜。sourceFrom/sourceTo 必须位于每个分镜对象内部；"
                + "soundSegmentIds 只允许 DIALOGUE、NARRATION、INNER_OS，禁止 ACTION、METADATA。"
                + "请直接规划整集并调用 save_episode_storyboards：\n"
                + writeJson(prepared)));
        return stepNo;
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
        WorkflowToolRunState state,
        String agentCode,
        WorkflowAgentRunContract contract,
        boolean reviewTruncationRecovery,
        boolean reviewEvidenceRefreshPending
    ) {
        if ("script-review".equals(agentCode) && reviewEvidenceRefreshPending) {
            return allowedTools.stream()
                .filter(tool -> "read_review_content".equals(tool.code()))
                .map(this::providerTool)
                .toList();
        }
        if ("script-review".equals(agentCode) && reviewTruncationRecovery) {
            Set<String> activeCodes = new HashSet<>(remainingContractTools(contract, state));
            return allowedTools.stream()
                .filter(tool -> activeCodes.contains(tool.code()))
                .map(this::providerTool)
                .toList();
        }
        if ("short-drama-asset-recognition".equals(agentCode)) {
            List<String> remaining = remainingContractTools(contract, state);
            if (remaining.isEmpty()) return List.of();
            String next = remaining.get(0);
            return allowedTools.stream()
                .filter(tool -> next.equals(tool.code()))
                .map(this::providerTool)
                .toList();
        }
        if ("short-drama-storyboard".equals(agentCode)) {
            return allowedTools.stream()
                .filter(tool -> contract.isTerminal(tool.code()))
                .map(this::providerTool)
                .toList();
        }
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

    private List<String> remainingContractTools(
        WorkflowAgentRunContract contract,
        WorkflowToolRunState state
    ) {
        Set<String> completed = new HashSet<>(state.successfulToolCodes());
        return contract.requiredToolSequence().stream()
            .filter(toolCode -> !completed.contains(toolCode))
            .toList();
    }

    private boolean isTruncated(AiTextResponse response) {
        return response != null
            && (response.truncated() || "length".equalsIgnoreCase(response.finishReason()));
    }

    private boolean isReviewEvidenceValidationFailure(String toolCode, BusinessException error) {
        return "save_review_unit_result".equals(toolCode)
            && error.getErrorCode() == ErrorCode.VALIDATION_ERROR
            && "审核证据无法在当前范围正文中验证。".equals(error.getMessage());
    }

    private boolean disableThinking(String agentCode, boolean splitting) {
        return splitting || "script-review".equals(agentCode);
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
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("ok", false);
            body.put("errorCode", error.getErrorCode().name());
            body.put("message", error.getMessage());
            if (error instanceof WorkflowToolValidationException validation) {
                body.putAll(validation.details());
            }
            return json.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            return "{\"ok\":false}";
        }
    }

    private String storyboardValidationCode(BusinessException error) {
        if (error instanceof WorkflowToolValidationException validation) {
            Object value = validation.details().get("validationCode");
            if (value != null) return value.toString();
        }
        return error.getErrorCode().name();
    }

    private String writeJson(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
                "无法序列化分镜规划上下文。");
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
