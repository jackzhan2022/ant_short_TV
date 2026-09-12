package com.antshorttv.workflowagent.run;

import com.antshorttv.script.ScriptSourceSegmentIndex;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkflowAgentRunner {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowAgentRunner.class);
    private static final Set<String> TRUSTED_SCOPE_ARGUMENTS = Set.of(
        "tenantId", "userId", "projectId", "episodeId", "scriptId", "taskId",
        "analysisStageId", "agentRunId", "permissions"
    );
    private static final List<String> EPISODE_SHARED_TOOL_SCHEMA = List.of(
        "read_current_episode", "read_adjacent_episodes", "read_script_analysis",
        "read_project_context", "read_script_assets", "save_episode_summary",
        "save_episode_assets", "save_episode_storyboards");

    private final WorkflowAgentService agents;
    private final WorkflowSkillService skills;
    private final WorkflowToolRegistry tools;
    private final WorkflowToolSchemaValidator schemaValidator;
    private final AiInvocationService invocation;
    private final WorkflowAgentRunRepository runs;
    private final WorkflowAgentScopeGuard scopeGuard;
    private final WorkflowAgentProperties properties;
    private final ObjectMapper json;
    private final StoryboardContextReducer storyboardContextReducer;
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
        StoryboardContextReducer storyboardContextReducer,
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
        this.storyboardContextReducer = storyboardContextReducer;
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
        ObjectMapper json,
        EpisodeSplittingRunPolicy splitPolicy
    ) {
        this(agents, skills, tools, schemaValidator, invocation, runs, scopeGuard,
            properties, json, new StoryboardContextReducer(json), splitPolicy);
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
        boolean deepReview = input.reviewScope() != null
            && isDeepReviewPhase(input.reviewScope().phase());
        int effectiveMaxSteps = deepReview
            ? Math.max(agent.maxSteps(), properties.getReviewDeepMaxSteps()) : agent.maxSteps();
        if (input.modelIdOverride() != null) {
            agents.requireToolCallingModel(effectiveModelId);
        }
        Long runId = runs.start(new WorkflowAgentRunStart(
            agent.id(), agent.code(), runType, input.tenantId(), input.userId(), input.projectId(),
            input.episodeId(), input.scriptId(), input.taskId(), input.analysisStageId(), effectiveModelId,
            agent.temperature(), agent.maxTokens(), effectiveMaxSteps, prompt, skillSnapshots,
            agent.toolCodes()
        ));
        long timeoutSeconds = deepReview
            ? Math.max(properties.getRunTimeoutSeconds(), properties.getReviewDeepRunTimeoutSeconds())
            : stageRunTimeoutSeconds(agent.code());
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        try {
            return runLoop(runId, agent, effectiveModelId, input, prompt, allowedTools, deadline, effectiveMaxSteps,
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

    private boolean isDeepReviewPhase(String phase) {
        return "MARKDOWN_DEEP_CHILD".equals(phase)
            || "MARKDOWN_DEEP_AGGREGATION".equals(phase);
    }

    private long stageRunTimeoutSeconds(String agentCode) {
        return switch (agentCode) {
            case "short-drama-episode-summary" -> properties.getEpisodeSummaryRunTimeoutSeconds();
            case "short-drama-asset-recognition" -> properties.getAssetRecognitionRunTimeoutSeconds();
            case "short-drama-storyboard" -> properties.getStoryboardRunTimeoutSeconds();
            default -> properties.getRunTimeoutSeconds();
        };
    }

    private int stageRequestTimeoutSeconds(String agentCode) {
        return switch (agentCode) {
            case "short-drama-episode-summary" -> properties.getEpisodeSummaryRequestTimeoutSeconds();
            case "short-drama-asset-recognition" -> properties.getAssetRecognitionRequestTimeoutSeconds();
            case "short-drama-storyboard" -> properties.getStoryboardRequestTimeoutSeconds();
            default -> Integer.MAX_VALUE;
        };
    }

    private WorkflowAgentRunResult runLoop(
        Long runId,
        WorkflowAgentRecord agent,
        Long modelId,
        WorkflowAgentRunInput input,
        String prompt,
        List<WorkflowToolDefinition> allowedTools,
        Instant deadline,
        int maxSteps,
        WorkflowAgentRunContract contract
    ) {
        WorkflowToolRunState runState = new WorkflowToolRunState();
        boolean splitting = EpisodeSplittingRunPolicy.AGENT_CODE.equals(agent.code());
        if (splitting) {
            runState.put(ScriptSourceSegmentIndex.RUN_STATE_KEY, true);
            prompt = prompt + "\n" + ScriptSourceSegmentIndex.INSTRUCTION;
        }
        if (splitting && splitPolicy != null) {
            splitPolicy.preflight(input).ifPresent(reason ->
                runState.beginSplitFallback(reason.name()));
        }
        input.promptCacheOptions().forEach(runState::put);
        input.trustedToolState().forEach(runState::put);
        List<AiChatMessage> messages = new ArrayList<>();
        if (input.stableContext() != null && !input.stableContext().isBlank()) {
            messages.add(AiChatMessage.system(input.stableContext()));
        }
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
        if (isTrustedEpisodePreloadAgent(agent.code())) {
            stepNo = prepareEpisodeContext(runId, agent, input, contract, context, messages,
                allowlist, deadline, stepNo);
        } else if ("short-drama-storyboard".equals(agent.code())) {
            stepNo = prepareStoryboardContext(runId, agent, input, contract, context, messages,
                allowlist, deadline, stepNo);
        }
        int assetSaveCorrections = 0;
        String traceId = "workflow-agent-" + UUID.randomUUID();
        for (int modelRound = 1; stepNo < maxSteps; modelRound++) {
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
                        null, Math.min(remainingSeconds(deadline), stageRequestTimeoutSeconds(agent.code())),
                        0,
                        messages, activeProviderTools(allowedTools, splitting, runState,
                            agent.code(), contract),
                        disableThinking(agent.code(), splitting) ? "disabled" : null,
                        input.promptCacheKey(), input.promptCacheOptions()
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
            if ("short-drama-asset-recognition".equals(agent.code()) && isTruncated(response)) {
                throw new WorkflowAgentTruncatedOutputException(runId, finalContent, modelCalls);
            }
            if ("script-review".equals(agent.code()) && isTruncated(response)) {
                throw new WorkflowAgentTruncatedOutputException(runId, finalContent, modelCalls);
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
                        arguments = EpisodeAssetsPayloadNormalizer.prepare(arguments, definition.inputSchema(),
                            context.runState().get("currentEpisodeContent", String.class));
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
                    if (contract.isTerminal(call.code())) {
                        runs.complete(runId, serialized);
                        return new WorkflowAgentRunResult(runId, serialized, modelCalls);
                    }
                } catch (Exception exception) {
                    BusinessException normalized = normalizeToolFailure(exception);
                    runs.recordFailedToolStep(runId, toolStep, call.code(), call.argumentsJson(),
                        normalized.getErrorCode().name(), normalized.getMessage());
                    if ("save_episode_assets".equals(call.code())) {
                        boolean correctable = exception instanceof BusinessException business
                            ? business.getErrorCode() == ErrorCode.VALIDATION_ERROR
                                || business.getErrorCode() == ErrorCode.WORKFLOW_AGENT_TOOL_INVALID
                            : exception instanceof IllegalArgumentException;
                        if (!correctable || assetSaveCorrections >= 2) throw normalized;
                        assetSaveCorrections++;
                        messages.add(AiChatMessage.toolResult(call.id(), writeError(normalized)));
                        messages.add(AiChatMessage.user(
                            "保存失败。仅修正错误中指出的字段并再次调用 save_episode_assets；"
                                + "五个数组必须始终存在，不得删除有效资产或清空数组来绕过校验。"
                                + "不得再次读取正文或其他上下文；优先使用当前预加载 sourceSegments 中的"
                                + " evidenceRef: {segmentId: \"S0001\"} 和 usageEvidenceRef，"
                                + "segmentId 必须实际存在且支持对应证据；证据不得编造。"));
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
            "Agent 已达到最大执行步数 " + maxSteps + "，仍未产生最终结果。");
    }

    private boolean isTrustedEpisodePreloadAgent(String agentCode) {
        return "short-drama-episode-summary".equals(agentCode)
            || "short-drama-asset-recognition".equals(agentCode);
    }

    private int prepareEpisodeContext(
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
        String toolCode = "read_current_episode";
        if (stepNo >= agent.maxSteps()) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_STEP_LIMIT,
                "Agent 步数不足，无法预加载当前剧集。");
        }
        requireBeforeDeadline(deadline);
        scopeGuard.requireExecutionActive(input);
        int toolStep = ++stepNo;
        if (!allowlist.contains(toolCode)) {
            BusinessException error = new BusinessException(
                ErrorCode.WORKFLOW_AGENT_TOOL_UNAUTHORIZED, "剧集预加载工具未获授权。");
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
            String handoff = "服务端已完成 read_current_episode，并已记录当前剧集的版本校验信息。"
                + "Agent/Skill 中先读取当前剧集的要求已由服务端完成，不要再次读取。"
                + "请基于已提供的当前剧集正文完成本阶段分析，直接调用 "
                + contract.terminalToolCode() + " 保存结果。";
            if ("short-drama-asset-recognition".equals(agent.code())) {
                handoff += "优先使用当前预加载 sourceSegments 中的 evidenceRef: {segmentId: \"S0001\"}"
                    + " 和 usageEvidenceRef 引用证据；segmentId 必须实际存在且支持对应资产或用途。"
                    + "不得再次读取正文；五个数组必须存在，不得删除有效资产或清空数组绕过校验。";
            }
            if (input.stableContext() == null || input.stableContext().isBlank()
                || "short-drama-asset-recognition".equals(agent.code())) {
                handoff += "\n以下为服务端已按可信作用域预加载并审计的当前剧集数据：\n" + serialized;
            }
            messages.add(AiChatMessage.user(handoff));
            return stepNo;
        } catch (Exception exception) {
            BusinessException normalized = normalizeToolFailure(exception);
            runs.recordFailedToolStep(runId, toolStep, toolCode, "{}",
                normalized.getErrorCode().name(), normalized.getMessage());
            throw normalized;
        }
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
        StoryboardContextReducer.Reduction reduction = storyboardContextReducer.reduce(prepared);
        LOG.info(
            "Storyboard context reduced runId={} episodeId={} originalChars={} reducedChars={} "
                + "adjacent={} characters={} scenes={} props={} optionalDropped={}",
            runId, input.episodeId(), reduction.originalCharacters(), reduction.reducedCharacters(),
            reduction.adjacentEpisodeCount(), reduction.characterCount(), reduction.sceneCount(),
            reduction.propCount(), reduction.optionalSectionsDropped());
        messages.add(AiChatMessage.user(
            "以下是服务端已按可信作用域读取并审计的完整分镜规划上下文。"
                + "不要再次读取，也不要引用旧分镜。sourceFrom/sourceTo 必须位于每个分镜对象内部；"
                + "soundSegmentIds 只允许 DIALOGUE、NARRATION、INNER_OS，禁止 ACTION、METADATA。"
                + "请直接规划整集并调用 save_episode_storyboards：\n"
                + writeJson(reduction.context())));
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
        WorkflowAgentRunContract contract
    ) {
        if ("short-drama-asset-recognition".equals(agentCode)
            || "short-drama-episode-summary".equals(agentCode)) {
            // The host has already read the episode; only the stage's authorized save remains.
            return allowedTools.stream()
                .filter(tool -> contract.isTerminal(tool.code()))
                .map(this::providerTool)
                .toList();
        }
        if ("short-drama-storyboard".equals(agentCode)) {
            return episodeSharedProviderTools();
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

    private List<AiToolDefinition> episodeSharedProviderTools() {
        return EPISODE_SHARED_TOOL_SCHEMA.stream()
            .filter(tools::contains)
            .map(tools::require)
            .map(this::providerTool)
            .toList();
    }

    private boolean isTruncated(AiTextResponse response) {
        return response != null
            && (response.truncated() || "length".equalsIgnoreCase(response.finishReason()));
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
