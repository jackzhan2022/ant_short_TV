package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionStatus;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.workflowagent.agent.EpisodeSplittingAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class ScriptWorkflowService {
    @Autowired(required = false)
    private ScriptGlobalUnderstandingRepository globalUnderstandingRepository;

    @Autowired
    private ScriptEpisodeService scriptEpisodeService;
    @Autowired
    private ScriptEpisodeSummaryRepository scriptEpisodeSummaryRepository;
    @Autowired
    private WorkflowAgentRunner workflowAgentRunner;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired(required = false)
    private StoryboardAgentAdapter storyboardAgentAdapter;

    private final ProjectAccessResolver projectAccessResolver;
    private final ProjectPermissionGuard projectPermissionGuard;
    private final ProjectMapper projectMapper;
    private final TenantContextResolver tenantContextResolver;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ScriptAnalysisTaskMapper scriptAnalysisTaskMapper;
    private final ScriptAnalysisStageMapper scriptAnalysisStageMapper;
    private final ScriptAnalysisResultMapper scriptAnalysisResultMapper;
    private final ScriptAnalysisTaskService scriptAnalysisTaskService;
    private final ScriptAnalysisExecutionCoordinator scriptAnalysisExecutionCoordinator;
    private final ProjectAiConfigService projectAiConfigService;
    private final AiInvocationService aiInvocationService;
    private final MaterialFileAccessService materialFileAccessService;
    private final TeamPointService teamPointService;
    private final ScriptElementExtractionService scriptElementExtractionService;
    private final ScriptAssetNormalizationService scriptAssetNormalizationService;
    private final ScriptAssetCandidateReviewService scriptAssetCandidateReviewService;
    private final AssetVisualVariantService assetVisualVariantService;
    private final AssetVisualBindingService assetVisualBindingService;
    private final EpisodeAwareVisualResolver episodeAwareVisualResolver;
    private final ScriptElementDraftService scriptElementDraftService;
    private final ScriptElementConfirmationService scriptElementConfirmationService;
    private final ScriptAiOperationService scriptAiOperationService;
    private final AiExecutionAttemptMapper executionAttemptMapper;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    public ScriptWorkflowService(
        ProjectAccessResolver projectAccessResolver,
        ProjectPermissionGuard projectPermissionGuard,
        ProjectMapper projectMapper,
        TenantContextResolver tenantContextResolver,
        ScriptMapper scriptMapper,
        ScriptVersionMapper scriptVersionMapper,
        ScriptAnalysisTaskMapper scriptAnalysisTaskMapper,
        ScriptAnalysisStageMapper scriptAnalysisStageMapper,
        ScriptAnalysisResultMapper scriptAnalysisResultMapper,
        ScriptAnalysisTaskService scriptAnalysisTaskService,
        ScriptAnalysisExecutionCoordinator scriptAnalysisExecutionCoordinator,
        ProjectAiConfigService projectAiConfigService,
        AiInvocationService aiInvocationService,
        MaterialFileAccessService materialFileAccessService,
        TeamPointService teamPointService,
        ScriptElementExtractionService scriptElementExtractionService,
        ScriptAssetNormalizationService scriptAssetNormalizationService,
        ScriptAssetCandidateReviewService scriptAssetCandidateReviewService,
        AssetVisualVariantService assetVisualVariantService,
        AssetVisualBindingService assetVisualBindingService,
        EpisodeAwareVisualResolver episodeAwareVisualResolver,
        ScriptElementDraftService scriptElementDraftService,
        ScriptElementConfirmationService scriptElementConfirmationService,
        ScriptAiOperationService scriptAiOperationService,
        AiExecutionAttemptMapper executionAttemptMapper,
        AiExecutionTaskMapper executionTaskMapper,
        PlatformTransactionManager transactionManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.projectAccessResolver = projectAccessResolver;
        this.projectPermissionGuard = projectPermissionGuard;
        this.projectMapper = projectMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.scriptAnalysisTaskMapper = scriptAnalysisTaskMapper;
        this.scriptAnalysisStageMapper = scriptAnalysisStageMapper;
        this.scriptAnalysisResultMapper = scriptAnalysisResultMapper;
        this.scriptAnalysisTaskService = scriptAnalysisTaskService;
        this.scriptAnalysisExecutionCoordinator = scriptAnalysisExecutionCoordinator;
        this.projectAiConfigService = projectAiConfigService;
        this.aiInvocationService = aiInvocationService;
        this.materialFileAccessService = materialFileAccessService;
        this.teamPointService = teamPointService;
        this.scriptElementExtractionService = scriptElementExtractionService;
        this.scriptAssetNormalizationService = scriptAssetNormalizationService;
        this.scriptAssetCandidateReviewService = scriptAssetCandidateReviewService;
        this.assetVisualVariantService = assetVisualVariantService;
        this.assetVisualBindingService = assetVisualBindingService;
        this.episodeAwareVisualResolver = episodeAwareVisualResolver;
        this.scriptElementDraftService = scriptElementDraftService;
        this.scriptElementConfirmationService = scriptElementConfirmationService;
        this.scriptAiOperationService = scriptAiOperationService;
        this.executionAttemptMapper = executionAttemptMapper;
        this.executionTaskMapper = executionTaskMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    public ScriptAiOperationExecutionResult executeGenerateOperation(
        ScriptAiOperationEntity operation,
        GenerateScriptRequest request,
        AiExecutionContext executionContext
    ) {
        ScriptVersionEntity completed = scriptVersionMapper.selectByExecutionId(executionContext.task().id);
        if (completed != null) {
            return new ScriptAiOperationExecutionResult("SCRIPT_VERSION", completed.getId(), List.of());
        }
        ProjectEntity project = projectMapper.selectByTenantIdAndId(operation.tenantId, operation.projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在。");
        }
        String title = resolveTitle(project, request);
        AiInvocationResult<AiTextResponse> invocation = invokeTextForExecution(
            executionContext,
            AiBusinessScene.SCRIPT_GENERATE,
            request.storyIdea(),
            buildScriptContent(title, request)
        );
        ScriptVersionEntity version = transactionTemplate.execute(status -> {
            requireActiveExecutionClaim(executionContext);
            ScriptVersionEntity raced = scriptVersionMapper.selectByExecutionId(executionContext.task().id);
            if (raced != null) {
                return raced;
            }
            LocalDateTime now = LocalDateTime.now();
            ScriptEntity script = scriptMapper.selectCurrentByProject(operation.tenantId, operation.projectId);
            if (script == null) {
                script = new ScriptEntity();
                script.setTenantId(operation.tenantId);
                script.setProjectId(operation.projectId);
                script.setCreatedBy(operation.createdBy);
                script.setCreatedAt(now);
            }
            script.setTitle(title);
            script.setSourceType("AI_GENERATE");
            script.setContent(invocation.content());
            script.setStatus("DRAFT");
            script.setUpdatedAt(now);
            if (script.getId() == null) {
                scriptMapper.insert(script);
            } else {
                scriptMapper.updateById(script);
            }

            ScriptVersionEntity created = new ScriptVersionEntity();
            created.setTenantId(operation.tenantId);
            created.setProjectId(operation.projectId);
            created.setScriptId(script.getId());
            created.setVersionNo(scriptVersionMapper.countByScript(operation.tenantId, script.getId()).intValue() + 1);
            created.setSourceType("AI_GENERATE");
            created.setInputSummary(request.storyIdea());
            created.setContent(invocation.content());
            created.setAiCallLogId(invocation.aiCallLogId());
            created.setExecutionId(executionContext.task().id);
            created.setStatus("DRAFT");
            created.setCreatedBy(operation.createdBy);
            created.setCreatedAt(now);
            scriptVersionMapper.insert(created);
            reconcileEpisodes(script, created);
            script.setCurrentVersionId(created.getId());
            scriptMapper.updateById(script);
            markOperationResult(operation, "SCRIPT_VERSION", created.getId());
            return created;
        });
        return new ScriptAiOperationExecutionResult("SCRIPT_VERSION", version.getId(), List.of(invocation));
    }

    public ScriptAiOperationExecutionResult executeRewriteOperation(
        ScriptAiOperationEntity operation,
        RewriteScriptRequest request,
        AiExecutionContext executionContext
    ) {
        if (operation.resultId != null) {
            return new ScriptAiOperationExecutionResult(operation.resultType, operation.resultId, List.of());
        }
        ScriptEntity script = scriptMapper.selectById(operation.scriptId);
        if (script == null || !operation.tenantId.equals(script.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本不存在。");
        }
        String requirement = blankToNull(request.requirement());
        AiInvocationResult<AiTextResponse> invocation = invokeAgentTextForExecution(
            executionContext,
            AiBusinessScene.SCRIPT_REWRITE,
            request.rewriteType().trim(),
            Map.of(
                "scriptContent", script.getContent(),
                "rewriteRequirement", requirement == null ? "保持原剧情核心" : requirement
            )
        );
        ScriptVersionEntity version = transactionTemplate.execute(status -> {
            requireActiveExecutionClaim(executionContext);
            ScriptVersionEntity raced = scriptVersionMapper.selectByExecutionId(executionContext.task().id);
            if (raced != null) {
                return raced;
            }
            LocalDateTime now = LocalDateTime.now();
            script.setSourceType("AI_REWRITE");
            script.setContent(invocation.content());
            script.setStatus("DRAFT");
            script.setUpdatedAt(now);
            scriptMapper.updateById(script);
            TenantContext owner = new TenantContext(operation.createdBy, operation.tenantId, null, null);
            ScriptVersionEntity created = createVersion(
                owner,
                operation.projectId,
                script.getId(),
                "AI_REWRITE",
                request.rewriteType().trim(),
                invocation.content(),
                invocation.aiCallLogId(),
                now
            );
            created.setExecutionId(executionContext.task().id);
            scriptVersionMapper.updateById(created);
            script.setCurrentVersionId(created.getId());
            scriptMapper.updateById(script);
            markOperationResult(operation, "SCRIPT_VERSION", created.getId());
            return created;
        });
        return new ScriptAiOperationExecutionResult("SCRIPT_VERSION", version.getId(), List.of(invocation));
    }

    public ScriptAiOperationExecutionResult executeStoryboardOperation(
        ScriptAiOperationEntity operation,
        StoryboardBreakdownRequest request,
        AiExecutionContext executionContext
    ) {
        if (operation.resultId != null) {
            return new ScriptAiOperationExecutionResult(operation.resultType, operation.resultId, List.of());
        }
        if (storyboardAgentAdapter == null || !storyboardAgentAdapter.enabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜 Workflow Agent 尚未启用。");
        }
        requireActiveExecutionClaim(executionContext);
        StoryboardAgentAdapter.Execution agent = storyboardAgentAdapter.execute(
            operation, request.episodeId(), executionContext);
        markOperationResult(operation, "STORYBOARD_SET", request.episodeId());
        return new ScriptAiOperationExecutionResult(
            "STORYBOARD_SET", request.episodeId(), List.of(), agent.modelCalls());
    }

    public ScriptAiOperationExecutionResult executeElementExtractionOperation(
        ScriptAiOperationEntity operation,
        ExtractScriptElementsRequest request,
        AiExecutionContext executionContext
    ) {
        if (operation.resultId != null) {
            return new ScriptAiOperationExecutionResult(operation.resultType, operation.resultId, List.of());
        }
        ScriptEntity script = scriptMapper.selectById(operation.scriptId);
        if (script == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本不存在。");
        }
        ScriptElementType elementType = ScriptElementType.from(request.elementType());
        TenantContext owner = new TenantContext(operation.createdBy, operation.tenantId, null, null);
        ScriptElementExtractionExecutionResult extraction = scriptElementExtractionService.extractWithExecution(
            owner,
            operation.projectId,
            script,
            elementType,
            executionContext
        );
        Long lastNormalizationRunId = null;
        for (AiInvocationResult<AiTextResponse> invocation : extraction.invocations()) {
            ScriptAssetNormalizationService.NormalizationPersistenceResult normalization =
                scriptAssetNormalizationService.normalizePartialAndPersist(
                    operation.tenantId,
                    operation.projectId,
                    operation.scriptId,
                    operation.scriptVersionId,
                    null,
                    null,
                    executionContext.task().id,
                    executionContext.claim().attemptId(),
                    invocation.aiCallLogId(),
                    invocation.idempotencyKey() == null
                        ? "script-element-operation:%d:%s".formatted(operation.id, invocation.businessSceneCode())
                        : invocation.idempotencyKey(),
                    invocation.content()
                );
            lastNormalizationRunId = normalization.runId();
            if (!normalization.valid()) {
                throw new BusinessException(
                    ErrorCode.AI_RESPONSE_INVALID,
                    "资产提取结果未通过归一化校验，候选与调用证据已保留。"
                );
            }
        }
        Long normalizationResultId = lastNormalizationRunId;
        transactionTemplate.executeWithoutResult(status -> {
            requireActiveExecutionClaim(executionContext);
            markOperationResult(operation, "ASSET_CANDIDATES", normalizationResultId);
        });
        return new ScriptAiOperationExecutionResult(
            "ASSET_CANDIDATES",
            normalizationResultId,
            extraction.invocations()
        );
    }

    public ScriptAiOperationExecutionResult executePromptOperation(
        ScriptAiOperationEntity operation,
        GeneratePromptRequest request,
        AiExecutionContext executionContext
    ) {
        if (operation.resultId != null) {
            return new ScriptAiOperationExecutionResult(operation.resultType, operation.resultId, List.of());
        }
        String targetType = normalizePromptTarget(request.targetType());
        AiInvocationResult<AiTextResponse> invocation = invokeTextForExecution(
            executionContext,
            AiBusinessScene.PROMPT_GENERATE,
            targetType,
            "生成提示词成功"
        );
        transactionTemplate.executeWithoutResult(status -> {
            requireActiveExecutionClaim(executionContext);
            applyGeneratedPrompts(operation.tenantId, operation.projectId, targetType);
            markOperationResult(operation, "SCRIPT_PROMPTS", operation.projectId);
        });
        return new ScriptAiOperationExecutionResult("SCRIPT_PROMPTS", operation.projectId, List.of(invocation));
    }

    private AiInvocationResult<AiTextResponse> invokeAgentTextForExecution(
        AiExecutionContext context,
        AiBusinessScene scene,
        String requestSummary,
        Map<String, Object> variables
    ) {
        AiExecutionTaskEntity execution = context.task();
        AiExecutionAttemptEntity attempt = executionAttemptMapper.selectById(context.claim().attemptId());
        return aiInvocationService.invokeText(AiInvocationRequest.text()
            .tenantId(execution.tenantId)
            .userId(execution.userId)
            .projectId(execution.projectId)
            .taskId(execution.businessId)
            .modelId(execution.requestedModelId)
            .scene(scene)
            .traceId(execution.traceId)
            .executionId(execution.id)
            .attemptId(context.claim().attemptId())
            .executionVersion(execution.executionVersion)
            .phase(context.claim().phase())
            .idempotencyKey(attempt.idempotencyKey)
            .requestSummary(requestSummary)
            .promptTemplateId(scene.agentCode())
            .templateVariables(variables)
            .build());
    }

    private void markOperationResult(ScriptAiOperationEntity operation, String resultType, Long resultId) {
        operation.resultType = resultType;
        operation.resultId = resultId;
        operation.updatedAt = LocalDateTime.now();
        scriptAiOperationService.updateResult(operation);
    }

    private AiInvocationResult<AiTextResponse> invokeTextForExecution(
        AiExecutionContext context,
        AiBusinessScene scene,
        String requestSummary,
        String prompt
    ) {
        AiExecutionTaskEntity execution = context.task();
        AiExecutionAttemptEntity attempt = executionAttemptMapper.selectById(context.claim().attemptId());
        return aiInvocationService.invokeText(AiInvocationRequest.text()
            .tenantId(execution.tenantId)
            .userId(execution.userId)
            .projectId(execution.projectId)
            .taskId(execution.businessId)
            .modelId(execution.requestedModelId)
            .scene(scene)
            .traceId(execution.traceId)
            .executionId(execution.id)
            .attemptId(context.claim().attemptId())
            .executionVersion(execution.executionVersion)
            .phase(context.claim().phase())
            .idempotencyKey(attempt.idempotencyKey)
            .requestSummary(requestSummary)
            .userPrompt(prompt)
            .build());
    }

    private void requireActiveExecutionClaim(AiExecutionContext context) {
        AiExecutionTaskEntity latest = executionTaskMapper.selectById(context.task().id);
        if (latest == null
            || !AiExecutionStatus.RUNNING.name().equals(latest.status)
            || !context.claim().claimToken().equals(latest.claimToken)) {
            throw new AiExecutionClaimLostException(context.task().id);
        }
    }

    public AiExecutionResponse submitGenerate(
        Long tenantId,
        Long projectId,
        GenerateScriptRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:AI_GENERATE", projectId);
        return submitOperation(context, projectId, AiBusinessScene.SCRIPT_GENERATE, "SCRIPT_GENERATE", null, null, request, servletRequest);
    }

    public AiExecutionResponse submitRewrite(
        Long tenantId,
        Long projectId,
        RewriteScriptRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:AI_REWRITE", projectId);
        ScriptEntity script = requireScript(tenantId, projectId);
        return submitOperation(context, projectId, AiBusinessScene.SCRIPT_REWRITE, "SCRIPT_REWRITE", script.getId(), script.getCurrentVersionId(), request, servletRequest);
    }

    public AiExecutionResponse submitExtractElements(
        Long tenantId,
        Long projectId,
        ExtractScriptElementsRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:AI_EXTRACT", projectId);
        ScriptEntity script = requireScript(tenantId, projectId);
        ScriptElementType elementType = ScriptElementType.from(request.elementType());
        AiBusinessScene scene = switch (elementType) {
            case CHARACTER -> AiBusinessScene.CHARACTER_EXTRACT;
            case SCENE -> AiBusinessScene.SCENE_EXTRACT;
            case PROP -> AiBusinessScene.PROP_EXTRACT;
            case ALL -> AiBusinessScene.SCRIPT_ELEMENT_EXTRACT;
        };
        return submitOperation(context, projectId, scene, "ELEMENT_EXTRACT", script.getId(), script.getCurrentVersionId(), request, servletRequest);
    }

    public AiExecutionResponse submitStoryboardBreakdown(
        Long tenantId,
        Long projectId,
        StoryboardBreakdownRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:AI_BREAKDOWN", projectId);
        ScriptEntity script = requireScript(tenantId, projectId);
        Integer episodeCount = jdbcTemplate.queryForObject("""
            select count(*) from script_episode
             where id = ? and tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null
            """, Integer.class, request.episodeId(), tenantId, projectId, script.getId());
        if (episodeCount == null || episodeCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择当前项目的一集有效剧集。");
        }
        return submitOperation(context, projectId, AiBusinessScene.STORYBOARD_BREAKDOWN, "STORYBOARD_BREAKDOWN", script.getId(), script.getCurrentVersionId(), request, servletRequest);
    }

    public AiExecutionResponse submitPromptGeneration(
        Long tenantId,
        Long projectId,
        GeneratePromptRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "PROMPT:AI_GENERATE", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        return submitOperation(
            context,
            projectId,
            AiBusinessScene.PROMPT_GENERATE,
            "PROMPT_GENERATE",
            script == null ? null : script.getId(),
            script == null ? null : script.getCurrentVersionId(),
            request,
            servletRequest
        );
    }

    private AiExecutionResponse submitOperation(
        TenantContext context,
        Long projectId,
        AiBusinessScene scene,
        String operationType,
        Long scriptId,
        Long scriptVersionId,
        Object input,
        HttpServletRequest request
    ) {
        return scriptAiOperationService.submit(
            context,
            projectId,
            scene,
            operationType,
            scriptId,
            scriptVersionId,
            input,
            requestHeaderOrUuid(request, "Idempotency-Key"),
            requestHeaderOrUuid(request, "X-Trace-Id")
        );
    }

    private String requestHeaderOrUuid(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    public ScriptWorkspaceResponse workspace(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        List<ScriptVersionResponse> versions = script == null
            ? List.of()
            : scriptVersionMapper.selectByScript(tenantId, script.getId())
                .stream()
                .map(ScriptVersionResponse::from)
                .toList();
        List<ScriptEpisodeResponse> episodes = script == null
            ? List.of()
            : scriptEpisodeService.currentEpisodes(tenantId, projectId, script.getId());
        if (episodes.isEmpty()) {
            episodes = ScriptEpisodeParser.parse(script == null ? null : script.getContent());
        }
        return new ScriptWorkspaceResponse(
            projectId,
            ScriptResponse.from(script),
            versions,
            characters(tenantId, projectId, script == null ? null : script.getId()),
            scenes(tenantId, projectId, script == null ? null : script.getId()),
            props(tenantId, projectId, script == null ? null : script.getId()),
            storyboards(tenantId, projectId),
            episodes,
            analysis(tenantId, projectId, script),
            script == null || globalUnderstandingRepository == null
                ? null
                : globalUnderstandingRepository.findCurrent(tenantId, script.getId())
                    .map(ScriptGlobalUnderstandingResponse::from)
                    .orElse(null)
        );
    }

    public ScriptAnalysisTaskResponse currentAnalysis(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, project.id);
        return analysis(tenantId, projectId, script);
    }

    @Transactional
    public AiExecutionResponse retryAnalysis(Long tenantId, Long projectId, String stageCode) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        ScriptAnalysisTaskEntity task = scriptAnalysisTaskService.retryStage(tenantId, projectId, stageCode);
        return scriptAnalysisExecutionCoordinator.retry(task);
    }

    @Transactional
    public AiExecutionResponse reanalyze(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null || script.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前项目暂无可重新分析的剧本版本。");
        }
        return reanalyzeVersion(tenantId, projectId, script.getCurrentVersionId(), context);
    }

    @Transactional
    public AiExecutionResponse reanalyzeVersion(Long tenantId, Long projectId, Long versionId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        return reanalyzeVersion(tenantId, projectId, versionId, context);
    }

    private AiExecutionResponse reanalyzeVersion(
        Long tenantId,
        Long projectId,
        Long versionId,
        TenantContext context
    ) {
        ScriptVersionEntity version = scriptVersionMapper.selectById(versionId);
        if (version == null || !tenantId.equals(version.getTenantId()) || !projectId.equals(version.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本版本不存在。");
        }
        ScriptEntity script = scriptMapper.selectById(version.getScriptId());
        if (script == null || !tenantId.equals(script.getTenantId()) || !projectId.equals(script.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本不存在。");
        }
        ScriptAnalysisTaskEntity task = scriptAnalysisTaskService.createManualTask(
            tenantId,
            projectId,
            script,
            version,
            context.userId(),
            LocalDateTime.now()
        );
        if (task == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前剧本内容为空，无法重新分析。");
        }
        return scriptAnalysisExecutionCoordinator.submitManual(task);
    }

    private ScriptAnalysisTaskResponse analysis(Long tenantId, Long projectId, ScriptEntity script) {
        if (script == null || script.getCurrentVersionId() == null) {
            return null;
        }
        ScriptAnalysisTaskEntity task = scriptAnalysisTaskMapper.selectLatestByVersion(
            tenantId,
            projectId,
            script.getCurrentVersionId()
        );
        return task == null ? null : analysisResponse(task);
    }

    private ScriptAnalysisTaskResponse analysisResponse(ScriptAnalysisTaskEntity task) {
        List<ScriptAnalysisStageEntity> stages = scriptAnalysisStageMapper.selectByTask(task.getId());
        Map<Long, ScriptAnalysisResultEntity> results = new LinkedHashMap<>();
        Map<Long, Long> agentRuns = new LinkedHashMap<>();
        Map<Long, EpisodeFanoutProgressResponse> fanouts = new LinkedHashMap<>();
        Map<Long, EpisodeSplitProgressResponse> splitProgress = new LinkedHashMap<>();
        for (ScriptAnalysisStageEntity stage : stages) {
            ScriptAnalysisResultEntity result = scriptAnalysisResultMapper.selectLatestByStage(stage.getId());
            if (result != null) {
                results.put(stage.getId(), result);
            }
            List<Long> runIds = jdbcTemplate.queryForList("""
                select id from ai_workflow_agent_run
                 where analysis_stage_id = ?
                 order by created_at desc, id desc
                 limit 1
                """, Long.class, stage.getId());
            if (!runIds.isEmpty()) {
                agentRuns.put(stage.getId(), runIds.get(0));
            }
            EpisodeFanoutProgressResponse fanout = fanoutProgress(stage.getId());
            if (fanout != null) fanouts.put(stage.getId(), fanout);
            if ("EPISODE_SPLITTING".equals(stage.getStageCode())) {
                splitProgress.put(stage.getId(), splitProgress(agentRuns.get(stage.getId())));
            }
        }
        return ScriptAnalysisTaskResponse.from(
            task, stages, results, agentRuns, fanouts, splitProgress);
    }

    private EpisodeSplitProgressResponse splitProgress(Long runId) {
        if (runId == null) {
            return new EpisodeSplitProgressResponse("FULL", null, 0, 0, 0, false);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select mode, fallback_reason, status, total_chunks, completed_chunks, failed_chunks
              from script_split_snapshot where parent_run_id = ?
             order by created_at desc, id desc limit 1
            """, runId);
        if (rows.isEmpty()) {
            return new EpisodeSplitProgressResponse("FULL", null, 0, 0, 0, false);
        }
        Map<String, Object> row = rows.get(0);
        return new EpisodeSplitProgressResponse(
            String.valueOf(row.get("mode")),
            row.get("fallback_reason") == null ? null : String.valueOf(row.get("fallback_reason")),
            ((Number) row.get("total_chunks")).intValue(),
            ((Number) row.get("completed_chunks")).intValue(),
            ((Number) row.get("failed_chunks")).intValue(),
            "STALE".equals(String.valueOf(row.get("status"))));
    }

    private EpisodeFanoutProgressResponse fanoutProgress(Long stageId) {
        List<Map<String, Object>> snapshots = jdbcTemplate.queryForList("""
            select id, status, total_units, completed_units, failed_units, episode_set_hash
              from script_analysis_fanout_snapshot
             where stage_id = ? order by attempt_no desc, id desc limit 1
            """, stageId);
        if (snapshots.isEmpty()) return null;
        Map<String, Object> snapshot = snapshots.get(0);
        long snapshotId = ((Number) snapshot.get("id")).longValue();
        List<EpisodeFanoutUnitResponse> units = jdbcTemplate.queryForList("""
            select episode_id, episode_key, status, child_run_id, error_code, error_message
              from script_analysis_fanout_unit where snapshot_id = ? order by id
            """, snapshotId).stream().map(row -> new EpisodeFanoutUnitResponse(
                ((Number) row.get("episode_id")).longValue(), String.valueOf(row.get("episode_key")),
                String.valueOf(row.get("status")),
                row.get("child_run_id") instanceof Number number ? number.longValue() : null,
                row.get("error_code") == null ? null : String.valueOf(row.get("error_code")),
                row.get("error_message") == null ? null : String.valueOf(row.get("error_message"))
            )).toList();
        EpisodeFanoutUnitResponse current = units.stream()
            .filter(unit -> "RUNNING".equals(unit.status())).findFirst().orElse(null);
        String status = String.valueOf(snapshot.get("status"));
        return new EpisodeFanoutProgressResponse(
            snapshotId, status,
            ((Number) snapshot.get("total_units")).intValue(),
            ((Number) snapshot.get("completed_units")).intValue(),
            ((Number) snapshot.get("failed_units")).intValue(),
            current == null ? null : current.episodeId(),
            current == null ? null : current.episodeKey(),
            units.stream().anyMatch(unit -> "FAILED".equals(unit.status())
                || "PENDING".equals(unit.status()) || "STALE".equals(unit.status())),
            "STALE".equals(status), units);
    }

    @Transactional
    public ScriptWorkspaceResponse generate(Long tenantId, Long projectId, GenerateScriptRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:AI_GENERATE", projectId);
        LocalDateTime now = LocalDateTime.now();
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null) {
            script = new ScriptEntity();
            script.setTenantId(tenantId);
            script.setProjectId(projectId);
            script.setCreatedBy(context.userId());
            script.setCreatedAt(now);
        }

        String title = resolveTitle(project, request);
        AiInvocationResult<AiTextResponse> invocation = callTextInvocation(
            context,
            projectId,
            AiBusinessScene.SCRIPT_GENERATE,
            request.storyIdea(),
            buildScriptContent(title, request)
        );
        String content = invocation.content();
        Long callLogId = invocation.aiCallLogId();
        script.setTitle(title);
        script.setSourceType("AI_GENERATE");
        script.setContent(content);
        script.setStatus("DRAFT");
        script.setUpdatedAt(now);
        if (script.getId() == null) {
            scriptMapper.insert(script);
        } else {
            scriptMapper.updateById(script);
        }

        ScriptVersionEntity version = new ScriptVersionEntity();
        version.setTenantId(tenantId);
        version.setProjectId(projectId);
        version.setScriptId(script.getId());
        version.setVersionNo(scriptVersionMapper.countByScript(tenantId, script.getId()).intValue() + 1);
        version.setSourceType("AI_GENERATE");
        version.setInputSummary(request.storyIdea());
        version.setContent(content);
        version.setAiCallLogId(callLogId);
        version.setStatus("DRAFT");
        version.setCreatedBy(context.userId());
        version.setCreatedAt(now);
        scriptVersionMapper.insert(version);
        reconcileEpisodes(script, version);

        script.setCurrentVersionId(version.getId());
        scriptMapper.updateById(script);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse rewrite(Long tenantId, Long projectId, RewriteScriptRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:AI_REWRITE", projectId);
        ScriptEntity script = requireScript(tenantId, projectId);
        String type = request.rewriteType().trim();
        String requirement = blankToNull(request.requirement());
        AiInvocationResult<AiTextResponse> invocation = callAgentTextInvocation(
            context,
            projectId,
            AiBusinessScene.SCRIPT_REWRITE,
            type,
            Map.of(
                "scriptContent", script.getContent(),
                "rewriteRequirement", requirement == null ? "保持原剧情核心" : requirement
            )
        );
        String content = invocation.content();
        Long callLogId = invocation.aiCallLogId();
        LocalDateTime now = LocalDateTime.now();

        script.setSourceType("AI_REWRITE");
        script.setContent(content);
        script.setStatus("DRAFT");
        script.setUpdatedAt(now);
        scriptMapper.updateById(script);

        ScriptVersionEntity version = createVersion(context, projectId, script.getId(), "AI_REWRITE", type, content, callLogId, now);
        script.setCurrentVersionId(version.getId());
        scriptMapper.updateById(script);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse saveCurrent(Long tenantId, Long projectId, SaveScriptRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:EDIT", projectId);
        LocalDateTime now = LocalDateTime.now();
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null) {
            script = new ScriptEntity();
            script.setTenantId(tenantId);
            script.setProjectId(projectId);
            script.setCreatedBy(context.userId());
            script.setCreatedAt(now);
        }
        script.setTitle(blankToNull(request.title()) == null ? project.name : request.title().trim());
        script.setSourceType("MANUAL_EDIT");
        script.setContent(request.content().trim());
        script.setStatus(normalizeStatus(request.status()));
        script.setUpdatedAt(now);
        if (script.getId() == null) {
            scriptMapper.insert(script);
        } else {
            scriptMapper.updateById(script);
        }
        ScriptVersionEntity version = createVersion(context, projectId, script.getId(), "MANUAL_EDIT", "手工保存剧本", script.getContent(), null, now);
        script.setCurrentVersionId(version.getId());
        scriptMapper.updateById(script);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse applyVersion(Long tenantId, Long projectId, Long versionId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:EDIT", projectId);
        ScriptVersionEntity version = scriptVersionMapper.selectById(versionId);
        if (version == null || !tenantId.equals(version.getTenantId()) || !projectId.equals(version.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本版本不存在。");
        }
        ScriptEntity script = scriptMapper.selectById(version.getScriptId());
        if (script == null || script.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本不存在。");
        }
        script.setContent(version.getContent());
        script.setSourceType(version.getSourceType());
        script.setStatus("CONFIRMED");
        script.setCurrentVersionId(version.getId());
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        reconcileEpisodes(script, version);
        version.setStatus("APPLIED");
        scriptVersionMapper.updateById(version);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse extractElements(Long tenantId, Long projectId, ExtractScriptElementsRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:AI_EXTRACT", projectId);
        throw new BusinessException(
            ErrorCode.AI_EXECUTION_STATUS_INVALID,
            "资产提取必须通过异步执行入口提交，以保留归一化与调用证据。"
        );
    }

    public WorkflowAgentRunResult regenerateEpisodeSplitting(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        requirePermission(context, "SCRIPT:EDIT", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null || script.getContent() == null || script.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前项目没有可智能拆分的剧本。");
        }
        return workflowAgentRunner.runFormal(new WorkflowAgentRunInput(
            EpisodeSplittingAgentBootstrap.AGENT_CODE,
            "基于当前剧本重新生成并覆盖正式剧集。",
            tenantId, projectId, null, script.getId(), null, null, context.userId()));
    }

    @Transactional
    public ScriptEpisodeSummaryDocument updateEpisodeSummary(
        Long tenantId,
        Long projectId,
        Long episodeId,
        SaveEpisodeSummaryRequest request
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:EDIT", projectId);
        Map<String, Object> episode = requireCurrentEpisode(tenantId, projectId, episodeId);
        Long scriptId = ((Number) episode.get("script_id")).longValue();
        if (scriptEpisodeSummaryRepository.findCurrent(tenantId, scriptId, episodeId).isPresent()
            && !Boolean.TRUE.equals(request.overwrite())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "覆盖现有剧集概要前必须明确确认 overwrite。");
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode content = mapper.createObjectNode();
        content.put("summary", request.summary());
        content.set("highlights", mapper.valueToTree(request.highlights()));
        if (request.endingHook() == null) content.putNull("endingHook");
        else content.put("endingHook", request.endingHook());
        scriptEpisodeSummaryRepository.upsert(new ScriptEpisodeSummaryDocument(
            null, tenantId, projectId, scriptId, episodeId, 1, content, "USER", null,
            context.userId(), context.userId(), null, null));
        jdbcTemplate.update("update script_episode set summary = ?, updated_at = now() where id = ?",
            request.summary(), episodeId);
        return scriptEpisodeSummaryRepository.findCurrent(tenantId, scriptId, episodeId).orElseThrow();
    }

    public WorkflowAgentRunResult regenerateEpisodeSummary(
        Long tenantId,
        Long projectId,
        Long episodeId,
        boolean overwrite
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        requirePermission(context, "SCRIPT:EDIT", projectId);
        Map<String, Object> episode = requireCurrentEpisode(tenantId, projectId, episodeId);
        Long scriptId = ((Number) episode.get("script_id")).longValue();
        if (scriptEpisodeSummaryRepository.findCurrent(tenantId, scriptId, episodeId).isPresent() && !overwrite) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "重新生成将覆盖现有概要，请明确确认 overwrite。");
        }
        return workflowAgentRunner.runFormal(new WorkflowAgentRunInput(
            com.antshorttv.workflowagent.agent.EpisodeSummaryAgentBootstrap.AGENT_CODE,
            "读取当前剧集并重新生成、覆盖正式概要。",
            tenantId, projectId, episodeId, scriptId, null, null, context.userId()));
    }

    public WorkflowAgentRunResult regenerateEpisodeAssets(
        Long tenantId,
        Long projectId,
        Long episodeId
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        requirePermission(context, "SCRIPT:EDIT", projectId);
        Map<String, Object> episode = requireCurrentEpisode(tenantId, projectId, episodeId);
        Long scriptId = ((Number) episode.get("script_id")).longValue();
        return workflowAgentRunner.runFormal(new WorkflowAgentRunInput(
            com.antshorttv.workflowagent.agent.AssetRecognitionAgentBootstrap.AGENT_CODE,
            "读取当前剧集并重新识别、匹配、覆盖本集正式角色、变装、场景、道具及形态绑定。",
            tenantId, projectId, episodeId, scriptId, null, null, context.userId()));
    }

    private Map<String, Object> requireCurrentEpisode(Long tenantId, Long projectId, Long episodeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select id, script_id from script_episode
             where id = ? and tenant_id = ? and project_id = ?
               and status = 'ACTIVE' and retired_at is null
            """, episodeId, tenantId, projectId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前正式剧集不存在。");
        }
        return rows.get(0);
    }

    public ScriptAssetCandidateReviewService.CandidatePage assetCandidates(
        Long tenantId, Long projectId, String reviewStatus, String assetType, Integer page, Integer pageSize
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:VIEW", projectId);
        return scriptAssetCandidateReviewService.listPage(
            tenantId, projectId, reviewStatus, assetType, page, pageSize);
    }

    public ScriptAssetCandidateReviewService.CandidateResponse assetCandidate(
        Long tenantId, Long projectId, Long candidateId
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:VIEW", projectId);
        return scriptAssetCandidateReviewService.detail(tenantId, projectId, candidateId);
    }

    public ScriptAssetCandidateReviewService.DecisionResponse decideAssetCandidate(
        Long tenantId,
        Long projectId,
        Long candidateId,
        ScriptAssetCandidateReviewService.DecisionCommand command
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        return scriptAssetCandidateReviewService.decide(
            tenantId, projectId, candidateId, context.userId(), command);
    }

    public List<AssetVisualVariantService.VariantResponse> visualVariants(
        Long tenantId, Long projectId, String assetType, Long assetId
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:VIEW", projectId);
        return assetVisualVariantService.list(tenantId, projectId, assetType, assetId);
    }

    public AssetVisualVariantService.VariantResponse createVisualVariant(
        Long tenantId, Long projectId, String assetType, Long assetId,
        AssetVisualVariantService.VariantCommand command
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        AssetVisualVariantService.VariantCommand editable = command == null ? null
            : new AssetVisualVariantService.VariantCommand(command.name(), command.appearance(), command.prompt(),
                "MANUAL", "NOT_STARTED", null, null, command.primary());
        return assetVisualVariantService.create(
            tenantId, projectId, assetType, assetId, context.userId(), editable);
    }

    public AssetVisualVariantService.VariantResponse updateVisualVariant(
        Long tenantId, Long projectId, Long variantId, AssetVisualVariantService.VariantCommand command
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        AssetVisualVariantService.VariantCommand editable = command == null ? null
            : new AssetVisualVariantService.VariantCommand(command.name(), command.appearance(), command.prompt(),
                null, null, null, null, command.primary());
        return assetVisualVariantService.update(tenantId, projectId, variantId, editable);
    }

    public AssetVisualVariantService.VariantResponse selectPrimaryVisualVariant(
        Long tenantId, Long projectId, Long variantId
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        return assetVisualVariantService.selectPrimary(tenantId, projectId, variantId);
    }

    public void deleteVisualVariant(Long tenantId, Long projectId, Long variantId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        assetVisualVariantService.delete(tenantId, projectId, variantId);
    }

    public List<AssetVisualBindingService.BindingResponse> bindVisualVariantEpisodes(
        Long tenantId, Long projectId, Long variantId, AssetVisualBindingService.BindingCommand command
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        return assetVisualBindingService.bind(
            tenantId, projectId, variantId, context.userId(), command);
    }

    public List<AssetVisualBindingService.BindingResponse> visualVariantBindings(
        Long tenantId, Long projectId, String assetType, Long assetId
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:VIEW", projectId);
        return assetVisualBindingService.list(tenantId, projectId, assetType, assetId);
    }

    @Transactional
    public ScriptWorkspaceResponse updateElement(Long tenantId, Long projectId, String elementType, Long elementId, UpdateScriptElementRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        switch (normalizeElementType(elementType)) {
            case "CHARACTER" -> jdbcTemplate.update("""
                update character_asset
                   set name = ?, role_type = ?, gender = ?, age_range = ?, identity = ?, personality = ?, appearance = ?, prompt = ?, status = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, request.name().trim(), defaultValue(request.roleType(), "SUPPORTING"), blankToNull(request.gender()), blankToNull(request.ageRange()), blankToNull(request.identity()), joinTags(request.personality()), blankToNull(request.appearance()), blankToNull(request.prompt()), normalizeStatus(request.status()), tenantId, projectId, elementId);
            case "SCENE" -> jdbcTemplate.update("""
                update scene_asset
                   set name = ?, scene_type = ?, time_atmosphere = ?, description = ?, visual_style = ?, prompt = ?, status = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, request.name().trim(), defaultValue(request.sceneType(), "INTERIOR"), blankToNull(request.atmosphere()), blankToNull(request.description()), blankToNull(request.visualStyle()), blankToNull(request.prompt()), normalizeStatus(request.status()), tenantId, projectId, elementId);
            case "PROP" -> jdbcTemplate.update("""
                update prop_asset
                   set name = ?, prop_type = ?, appearance = ?, plot_function = ?, related_character = ?, prompt = ?, status = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, request.name().trim(), defaultValue(request.propType(), "KEY_PROP"), blankToNull(request.appearance()), blankToNull(request.plotFunction()), blankToNull(request.relatedCharacter()), blankToNull(request.prompt()), normalizeStatus(request.status()), tenantId, projectId, elementId);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        }
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse confirmElement(Long tenantId, Long projectId, String elementType, Long elementId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        scriptElementConfirmationService.confirm(tenantId, projectId, ScriptElementType.from(elementType), elementId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse deleteElement(Long tenantId, Long projectId, String elementType, Long elementId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        jdbcTemplate.update("""
            update %s set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """.formatted(elementTable(normalizeElementType(elementType))), tenantId, projectId, elementId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse breakdownStoryboards(Long tenantId, Long projectId, StoryboardBreakdownRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:AI_BREAKDOWN", projectId);
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请使用异步的生成本集分镜接口。");
    }

    @Transactional
    public ScriptWorkspaceResponse createStoryboard(Long tenantId, Long projectId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        insertStoryboard(tenantId, projectId, script == null ? null : script.getId(), context.userId(), request.episodeNo(), request.shotNo(), request.sceneNo(), request.shotType(), request.visualDescription(), request.characters(), request.actions(), request.dialogue(), request.scene(), request.props(), request.mood(), request.durationSeconds(), request.imagePrompt(), request.videoPrompt(), normalizeStatus(request.status()));
        if (request.storyboardNo() != null || request.promptDocument() != null) {
            jdbcTemplate.update("""
                update storyboard set storyboard_no = coalesce(?, shot_no), prompt_document_json = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = (
                   select id from (select id from storyboard where tenant_id = ? and project_id = ?
                     and deleted_at is null order by id desc limit 1) latest)
                """, request.storyboardNo(), writeJson(request.promptDocument()), tenantId, projectId,
                tenantId, projectId);
        }
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse updateStoryboard(Long tenantId, Long projectId, Long storyboardId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard
               set episode_no = ?, shot_no = ?, storyboard_no = coalesce(?, storyboard_no, shot_no),
                   scene_no = ?, shot_type = ?, visual_description = ?, characters = ?, actions = ?,
                   dialogue = ?, scene = ?, props = ?, mood = ?, duration_seconds = ?, image_prompt = ?,
                   video_prompt = ?, prompt_document_json = coalesce(?, prompt_document_json), status = ?, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, request.episodeNo(), request.shotNo(), request.storyboardNo(), blankToNull(request.sceneNo()),
            blankToNull(request.shotType()), request.visualDescription().trim(), blankToNull(request.characters()),
            blankToNull(request.actions()), blankToNull(request.dialogue()), blankToNull(request.scene()),
            blankToNull(request.props()), blankToNull(request.mood()), request.durationSeconds(),
            blankToNull(request.imagePrompt()), blankToNull(request.videoPrompt()), writeJson(request.promptDocument()),
            normalizeStatus(request.status()), tenantId, projectId, storyboardId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse moveStoryboard(Long tenantId, Long projectId, Long storyboardId, MoveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard set shot_no = ?, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, request.shotNo(), tenantId, projectId, storyboardId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse confirmStoryboards(Long tenantId, Long projectId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard set status = 'CONFIRMED', updated_at = now()
             where tenant_id = ? and project_id = ? and deleted_at is null
            """, tenantId, projectId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse deleteStoryboard(Long tenantId, Long projectId, Long storyboardId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, tenantId, projectId, storyboardId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse generatePrompts(Long tenantId, Long projectId, GeneratePromptRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "PROMPT:AI_GENERATE", projectId);
        String targetType = normalizePromptTarget(request.targetType());
        applyGeneratedPrompts(tenantId, projectId, targetType);
        callTextInvocation(context, projectId, AiBusinessScene.PROMPT_GENERATE, targetType, "生成提示词成功");
        return workspace(tenantId, projectId);
    }

    private void applyGeneratedPrompts(Long tenantId, Long projectId, String targetType) {
        if ("ALL".equals(targetType) || "CHARACTER".equals(targetType)) {
            jdbcTemplate.update("""
                update character_asset
                   set prompt = concat('角色定妆提示词：', name, '，', coalesce(identity, ''), '，', coalesce(appearance, ''), '，竖屏短剧写实风格'), updated_at = now()
                 where tenant_id = ? and project_id = ? and deleted_at is null
                """, tenantId, projectId);
        }
        if ("ALL".equals(targetType) || "SCENE".equals(targetType)) {
            jdbcTemplate.update("""
                update scene_asset
                   set prompt = concat('场景图提示词：', name, '，', coalesce(description, ''), '，', coalesce(visual_style, ''), '，电影感光影'), updated_at = now()
                 where tenant_id = ? and project_id = ? and deleted_at is null
                """, tenantId, projectId);
        }
        if ("ALL".equals(targetType) || "PROP".equals(targetType)) {
            jdbcTemplate.update("""
                update prop_asset
                   set prompt = concat('道具图提示词：', name, '，', coalesce(appearance, ''), '，关键线索特写'), updated_at = now()
                 where tenant_id = ? and project_id = ? and deleted_at is null
                """, tenantId, projectId);
        }
        if ("ALL".equals(targetType) || "STORYBOARD".equals(targetType)) {
            jdbcTemplate.update("""
                update storyboard
                   set image_prompt = concat('首帧图片提示词：', visual_description, '，竖屏短剧，电影感'),
                       video_prompt = concat('竖屏短剧视频提示词：', coalesce(actions, visual_description), '，镜头自然运动，情绪连续'),
                       updated_at = now()
                 where tenant_id = ? and project_id = ? and deleted_at is null
                """, tenantId, projectId);
        }
    }

    private ProjectEntity requireProjectAccess(TenantContext context, Long projectId) {
        return projectAccessResolver.requireView(context.tenantId(), projectId).project();
    }

    private void requirePermission(TenantContext context, String permissionCode, Long projectId) {
        projectPermissionGuard.require(context.tenantId(), projectId, permissionCode);
    }

    private List<CharacterAssetResponse> characters(Long tenantId, Long projectId, Long scriptId) {
        return jdbcTemplate.query("""
            select id, name, role_type, gender, age_range, identity, personality, appearance, prompt,
                   status, merge_target_id, main_image_url
              from character_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
               and (script_id = ? or script_id is null)
             order by id
            """, (rs, rowNum) -> new CharacterAssetResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("role_type"),
                rs.getString("gender"),
                rs.getString("age_range"),
                rs.getString("identity"),
                splitTags(rs.getString("personality")),
                rs.getString("appearance"),
                rs.getString("prompt"),
                rs.getString("status"),
                rs.getObject("merge_target_id", Long.class),
                assetVisualWorkspace(tenantId, projectId, "CHARACTER", rs.getLong("id"))
            ), tenantId, projectId, scriptId);
    }

    private List<SceneAssetResponse> scenes(Long tenantId, Long projectId, Long scriptId) {
        return jdbcTemplate.query("""
            select id, name, scene_type, time_atmosphere, description, visual_style, prompt,
                   status, merge_target_id, main_image_url
              from scene_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
               and (script_id = ? or script_id is null)
             order by id
            """, (rs, rowNum) -> new SceneAssetResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("scene_type"),
                rs.getString("time_atmosphere"),
                rs.getString("description"),
                rs.getString("visual_style"),
                rs.getString("prompt"),
                rs.getString("status"),
                rs.getObject("merge_target_id", Long.class),
                assetVisualWorkspace(tenantId, projectId, "SCENE", rs.getLong("id"))
            ), tenantId, projectId, scriptId);
    }

    private List<PropAssetResponse> props(Long tenantId, Long projectId, Long scriptId) {
        return jdbcTemplate.query("""
            select id, name, prop_type, appearance, plot_function, prompt,
                   status, merge_target_id, main_image_url
              from prop_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
               and (script_id = ? or script_id is null)
             order by id
            """, (rs, rowNum) -> new PropAssetResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("prop_type"),
                rs.getString("appearance"),
                rs.getString("plot_function"),
                rs.getString("prompt"),
                rs.getString("status"),
                rs.getObject("merge_target_id", Long.class),
                assetVisualWorkspace(tenantId, projectId, "PROP", rs.getLong("id"))
            ), tenantId, projectId, scriptId);
    }

    private AssetVisualWorkspace assetVisualWorkspace(
        Long tenantId, Long projectId, String assetType, Long assetId
    ) {
        List<AssetVisualVariantService.VariantResponse> variants =
            assetVisualVariantService.list(tenantId, projectId, assetType, assetId);
        AssetVisualVariantService.VariantResponse primary = variants.stream()
            .filter(AssetVisualVariantService.VariantResponse::primary).findFirst().orElse(null);
        Map<String, Long> generationSummary = variants.stream().collect(java.util.stream.Collectors.groupingBy(
            AssetVisualVariantService.VariantResponse::generationStatus,
            java.util.LinkedHashMap::new,
            java.util.stream.Collectors.counting()));
        Long pending = jdbcTemplate.queryForObject("""
            select count(*) from script_asset_candidate
             where tenant_id = ? and project_id = ? and asset_type = ? and proposed_target_id = ?
               and review_status = 'PENDING_REVIEW'
            """, Long.class, tenantId, projectId, assetType, assetId);
        EpisodeAwareVisualResolver.ResolvedVisual resolved =
            episodeAwareVisualResolver.resolve(tenantId, projectId, assetType, assetId, null);
        return new AssetVisualWorkspace(variants.size(), primary, variants, generationSummary,
            assetVisualBindingService.list(tenantId, projectId, assetType, assetId),
            pending != null && pending > 0 ? "PENDING_REVIEW" : "NONE",
            resolved.imageUrl(), resolved.source());
    }

    private List<StoryboardResponse> storyboards(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, shot_no, coalesce(storyboard_no, shot_no) storyboard_no, episode_id, episode_no,
                   shot_type, visual_description, characters, scene, dialogue, duration_seconds,
                   shot_plan_json, prompt_document_json, material_binding_status, source_fingerprint,
                   generated_by_run_id, image_prompt, video_prompt, first_frame_url,
                   current_video_result_id, current_video_url
              from storyboard
             where tenant_id = ? and project_id = ? and deleted_at is null
             order by episode_no, shot_no, id
            """, (rs, rowNum) -> new StoryboardResponse(
                rs.getLong("id"),
                rs.getInt("shot_no"),
                rs.getInt("storyboard_no"),
                rs.getObject("episode_id", Long.class),
                rs.getInt("episode_no"),
                rs.getString("shot_type"),
                rs.getString("visual_description"),
                rs.getString("characters"),
                rs.getString("scene"),
                rs.getString("dialogue"),
                rs.getObject("duration_seconds", Integer.class),
                readJson(rs.getString("shot_plan_json")),
                readJson(rs.getString("prompt_document_json")),
                rs.getString("material_binding_status"),
                rs.getString("source_fingerprint"),
                rs.getObject("generated_by_run_id", Long.class),
                rs.getString("image_prompt"),
                rs.getString("video_prompt"),
                materialFileAccessService.publicUrl(rs.getString("first_frame_url")),
                rs.getObject("current_video_result_id", Long.class),
                materialFileAccessService.publicUrl(rs.getString("current_video_url"))
            ), tenantId, projectId);
    }

    private com.fasterxml.jackson.databind.JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("分镜结构化数据损坏。", exception);
        }
    }

    private String writeJson(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null ? null : value.toString();
    }

    private List<String> splitTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[、,，]"))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private ScriptEntity requireScript(Long tenantId, Long projectId) {
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null || script.getContent() == null || script.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前项目暂无可用剧本。");
        }
        return script;
    }

    private AiInvocationResult<AiTextResponse> callTextInvocation(
        TenantContext context,
        Long projectId,
        AiBusinessScene scene,
        String requestSummary,
        String fallbackContent
    ) {
        requireExecutionReservation();
        Long modelId = projectAiConfigService.resolveModelId(context.tenantId(), projectId, "TEXT");
        return aiInvocationService.invokeText(AiInvocationRequest.text()
            .tenantId(context.tenantId())
            .userId(context.userId())
            .projectId(projectId)
            .modelId(modelId)
            .scene(scene)
            .requestSummary(requestSummary)
            .userPrompt(fallbackContent == null ? requestSummary : fallbackContent)
            .build());
    }

    private AiInvocationResult<AiTextResponse> callAgentTextInvocation(
        TenantContext context,
        Long projectId,
        AiBusinessScene scene,
        String requestSummary,
        Map<String, Object> variables
    ) {
        requireExecutionReservation();
        Long modelId = projectAiConfigService.resolveModelId(context.tenantId(), projectId, "TEXT");
        return aiInvocationService.invokeText(AiInvocationRequest.text()
            .tenantId(context.tenantId())
            .userId(context.userId())
            .projectId(projectId)
            .modelId(modelId)
            .scene(scene)
            .requestSummary(requestSummary)
            .promptTemplateId(scene.agentCode())
            .templateVariables(variables)
            .build());
    }

    private void requireExecutionReservation() {
        throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "AI 调用必须先创建执行和积分预占。");
    }

    private ScriptVersionEntity createVersion(TenantContext context, Long projectId, Long scriptId, String sourceType, String inputSummary, String content, Long callLogId, LocalDateTime now) {
        ScriptVersionEntity version = new ScriptVersionEntity();
        version.setTenantId(context.tenantId());
        version.setProjectId(projectId);
        version.setScriptId(scriptId);
        version.setVersionNo(scriptVersionMapper.countByScript(context.tenantId(), scriptId).intValue() + 1);
        version.setSourceType(sourceType);
        version.setInputSummary(inputSummary);
        version.setContent(content);
        version.setAiCallLogId(callLogId);
        version.setStatus("DRAFT");
        version.setCreatedBy(context.userId());
        version.setCreatedAt(now);
        scriptVersionMapper.insert(version);
        ScriptEntity script = scriptMapper.selectById(scriptId);
        if (script != null) {
            reconcileEpisodes(script, version);
        }
        return version;
    }

    private void reconcileEpisodes(ScriptEntity script, ScriptVersionEntity version) {
        scriptEpisodeService.reconcileAndPersist(
            script.getTenantId(),
            script.getProjectId(),
            script.getId(),
            version.getId(),
            ScriptEpisodeParser.parse(version.getContent())
        );
    }

    private void insertStoryboard(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long userId,
        Integer episodeNo,
        Integer shotNo,
        String sceneNo,
        String shotType,
        String visualDescription,
        String characters,
        String actions,
        String dialogue,
        String scene,
        String props,
        String mood,
        Integer durationSeconds,
        String imagePrompt,
        String videoPrompt,
        String status
    ) {
        jdbcTemplate.update("""
            insert into storyboard
              (tenant_id, project_id, script_id, episode_no, shot_no, scene_no, shot_type, visual_description, characters, actions, dialogue, scene, props, mood, duration_seconds, image_prompt, video_prompt, status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
            tenantId,
            projectId,
            scriptId,
            episodeNo == null ? 1 : episodeNo,
            shotNo == null ? nextShotNo(tenantId, projectId, episodeNo == null ? 1 : episodeNo) : shotNo,
            blankToNull(sceneNo),
            defaultValue(shotType, "中景"),
            visualDescription.trim(),
            blankToNull(characters),
            blankToNull(actions),
            blankToNull(dialogue),
            blankToNull(scene),
            blankToNull(props),
            blankToNull(mood),
            durationSeconds == null ? 5 : durationSeconds,
            blankToNull(imagePrompt),
            blankToNull(videoPrompt),
            normalizeStatus(status),
            userId
        );
    }

    private int nextShotNo(Long tenantId, Long projectId, Integer episodeNo) {
        Integer max = jdbcTemplate.queryForObject("""
            select coalesce(max(shot_no), 0)
              from storyboard
             where tenant_id = ? and project_id = ? and episode_no = ? and deleted_at is null
            """, Integer.class, tenantId, projectId, episodeNo);
        return max == null ? 1 : max + 1;
    }

    private String normalizeElementType(String elementType) {
        String value = elementType == null ? "" : elementType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CHARACTER", "SCENE", "PROP").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        }
        return value;
    }

    private String normalizePromptTarget(String targetType) {
        String value = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "CHARACTER", "SCENE", "PROP", "STORYBOARD").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择提示词生成对象。");
        }
        return value;
    }

    private String elementTable(String elementType) {
        return switch (elementType) {
            case "CHARACTER" -> "character_asset";
            case "SCENE" -> "scene_asset";
            case "PROP" -> "prop_asset";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        };
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "CONFIRMED", "APPLIED", "PENDING_REVIEW").contains(value)) {
            return "DRAFT";
        }
        return value;
    }

    private String joinTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
            .map(item -> item == null ? "" : item.trim())
            .filter(item -> !item.isBlank())
            .reduce((left, right) -> left + "、" + right)
            .orElse("");
        return joined.isBlank() ? null : joined;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimSummary(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }

    private String resolveTitle(ProjectEntity project, GenerateScriptRequest request) {
        return request.title() == null || request.title().isBlank()
            ? project.name
            : request.title().trim();
    }

    private String buildScriptContent(String title, GenerateScriptRequest request) {
        int episodeCount = request.episodeCount() == null ? 12 : request.episodeCount();
        int duration = request.duration() == null ? 90 : request.duration();
        String style = request.styleRequirement() == null || request.styleRequirement().isBlank()
            ? "强冲突、快节奏"
            : request.styleRequirement().trim();
        return """
            剧名：《%s》
            题材：%s
            规格：%d集，每集约%d秒
            风格：%s

            故事简介：
            %s。故事围绕主角回归、身份反转和情感拉扯展开，以快节奏冲突推动每集结尾钩子。

            核心看点：
            1. 三秒进入冲突，快速建立主角困境。
            2. 每集结尾保留反转钩子。
            3. 人物关系持续升级，适合短剧连续追看。

            第1集
            场景一：雨夜，林家老宅门口。
            主角拖着行李箱站在铁门外，雨水顺着发梢落下。
            主角：三年前你们把我赶出去，今天我回来，只拿回属于我的东西。

            场景二：宴会厅。
            宾客的笑声戛然而止，旧日熟人在人群后方认出主角。
            旧日熟人：这不可能，她怎么会回来？

            本集钩子：
            主角拿出旧股权协议，协议末页却出现关键人物的签名。
            """.formatted(title, request.genre(), episodeCount, duration, style, request.storyIdea());
    }
}
