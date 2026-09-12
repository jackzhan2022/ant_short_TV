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
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.workflowagent.agent.EpisodeSplittingAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    @Autowired(required = false)
    private ScopedAssetReextractionService scopedAssetReextractionService;

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
    private final AssetVisualVariantService assetVisualVariantService;
    private final AssetVisualBindingService assetVisualBindingService;
    private final EpisodeAwareVisualResolver episodeAwareVisualResolver;
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
        AssetVisualVariantService assetVisualVariantService,
        AssetVisualBindingService assetVisualBindingService,
        EpisodeAwareVisualResolver episodeAwareVisualResolver,
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
        this.assetVisualVariantService = assetVisualVariantService;
        this.assetVisualBindingService = assetVisualBindingService;
        this.episodeAwareVisualResolver = episodeAwareVisualResolver;
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
        if (storyboardAgentAdapter == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜 Workflow Agent 尚未启用。");
        }
        requireActiveExecutionClaim(executionContext);
        StoryboardAgentAdapter.Execution agent = storyboardAgentAdapter.execute(
            operation, request.episodeId(), executionContext);
        markOperationResult(operation, "STORYBOARD_SET", request.episodeId());
        return new ScriptAiOperationExecutionResult(
            "STORYBOARD_SET", request.episodeId(), List.of(), agent.modelCalls());
    }


    public ScriptAiOperationExecutionResult executeScopedAssetReextractionOperation(
        ScriptAiOperationEntity operation,
        ScopedAssetReextractionRequest request,
        AiExecutionContext executionContext
    ) {
        if (scopedAssetReextractionService == null) {
            throw new IllegalStateException("资产重提取服务不可用。");
        }
        return scopedAssetReextractionService.execute(operation, request, executionContext);
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
        PromptBackfillTarget target = promptBackfillTarget(operation.tenantId, operation.projectId, targetType);
        if (target.empty()) {
            transactionTemplate.executeWithoutResult(status -> {
                requireActiveExecutionClaim(executionContext);
                markOperationResult(operation, "SCRIPT_PROMPTS", operation.projectId);
            });
            return new ScriptAiOperationExecutionResult("SCRIPT_PROMPTS", operation.projectId, List.of());
        }
        AiInvocationResult<AiTextResponse> invocation = invokeTextForExecution(
            executionContext,
            AiBusinessScene.PROMPT_GENERATE,
            targetType,
            target.request()
        );
        transactionTemplate.executeWithoutResult(status -> {
            requireActiveExecutionClaim(executionContext);
            applyGeneratedPrompts(operation.tenantId, operation.projectId, targetType, invocation.content());
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
            .promptTemplateId(scene.promptTemplateId())
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

    public ScopedAssetReextractionService.AssetReextractionPreflight assetReextractionPreflight(
        Long tenantId,
        Long projectId,
        String targetType
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:AI_EXTRACT", projectId);
        if (scopedAssetReextractionService == null) throw new IllegalStateException("资产重提取服务不可用。");
        return scopedAssetReextractionService.preflight(tenantId, projectId,
            requireScript(tenantId, projectId).getId(), parseAssetScope(targetType));
    }

    public AiExecutionResponse submitScopedAssetReextraction(
        Long tenantId,
        Long projectId,
        ScopedAssetReextractionRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:AI_EXTRACT", projectId);
        ScriptEntity script = requireScript(tenantId, projectId);
        parseAssetScope(request.targetType());
        parseAssetPromptPolicy(request.promptPolicy());
        return submitOperation(context, projectId, AiBusinessScene.SCOPED_ASSET_REEXTRACTION,
            "SCOPED_ASSET_REEXTRACTION", script.getId(), script.getCurrentVersionId(), request, servletRequest);
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


    public ScriptPageWorkspaceResponse scriptPageWorkspace(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        List<ScriptVersionSummaryResponse> versions = script == null ? List.of()
            : scriptVersionMapper.selectByScript(tenantId, script.getId()).stream()
                .map(ScriptVersionSummaryResponse::from).toList();
        List<ScriptEpisodeNavigation> navigation = script == null ? List.of()
            : scriptEpisodeService.currentEpisodeNavigation(tenantId, projectId, script.getId());
        if (navigation.isEmpty()) {
            navigation = ScriptEpisodeParser.parse(script == null ? null : script.getContent()).stream()
                .map(item -> new ScriptEpisodeNavigation(item.episodeId(), item.episodeNo(), item.title(), item.summary(),
                    item.contentFingerprint(), item.generatedByRunId(), item.formalSummary()))
                .toList();
        }
        return new ScriptPageWorkspaceResponse(projectId, ScriptPageScriptResponse.from(script), versions,
            navigation.stream().map(ScriptEpisodeSummaryResponse::from).toList(),
            new EpisodeSplitWarnings().inspect(script == null ? null : script.getContent(),
                navigation.stream().map(ScriptEpisodeNavigation::withoutContent).toList()),
            analysis(tenantId, projectId, script), script == null || globalUnderstandingRepository == null ? null
                : globalUnderstandingRepository.findCurrent(tenantId, script.getId())
                    .map(ScriptGlobalUnderstandingResponse::from).orElse(null));
    }

    public ScriptVersionResponse scriptVersion(Long tenantId, Long projectId, Long versionId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        ScriptVersionEntity version = scriptVersionMapper.selectById(versionId);
        if (version == null || !tenantId.equals(version.getTenantId()) || !projectId.equals(version.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧本版本不存在。");
        }
        return ScriptVersionResponse.from(version);
    }

    public ScriptEpisodeResponse scriptEpisode(Long tenantId, Long projectId, Long episodeId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        ScriptEpisodeResponse episode = scriptEpisodeService.currentEpisode(tenantId, projectId, episodeId);
        if (episode == null) throw new BusinessException(ErrorCode.NOT_FOUND, "剧集不存在。");
        return episode;
    }

    public ScriptResponse scriptContent(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        return ScriptResponse.from(scriptMapper.selectCurrentByProject(tenantId, projectId));
    }

    public AssetSettingsSummaryResponse assetSettingsSummary(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        Long scriptId = script == null ? null : script.getId();
        return new AssetSettingsSummaryResponse(projectId,
            characterSummaries(tenantId, projectId, scriptId), sceneSummaries(tenantId, projectId, scriptId),
            propSummaries(tenantId, projectId, scriptId));
    }

    public AssetVisualWorkspace assetVisualWorkspace(Long tenantId, Long projectId, String assetType, Long assetId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectAccessContext access = requireProjectAccessContext(context, projectId);
        requirePermission(access, "ELEMENT:VIEW");
        return buildAssetVisualWorkspace(tenantId, projectId, normalizeElementType(assetType), assetId);
    }

    public StoryboardWorkspacePageResponse storyboardWorkspace(Long tenantId, Long projectId, Integer episodeNo,
        Integer current, Integer pageSize) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        List<ScriptEpisodeNavigation> navigation = script == null ? List.of()
            : scriptEpisodeService.currentEpisodeNavigation(tenantId, projectId, script.getId());
        if (navigation.isEmpty()) {
            navigation = ScriptEpisodeParser.parse(script == null ? null : script.getContent()).stream()
                .map(item -> new ScriptEpisodeNavigation(item.episodeId(), item.episodeNo(), item.title(), item.summary(),
                    item.contentFingerprint(), item.generatedByRunId(), item.formalSummary()))
                .toList();
        }
        int selectedEpisode = episodeNo == null ? navigation.stream().findFirst().map(ScriptEpisodeNavigation::episodeNo)
            .orElse(1) : episodeNo;
        Long total = jdbcTemplate.queryForObject("""
            select count(*) from storyboard where tenant_id = ? and project_id = ? and episode_no = ? and deleted_at is null
            """, Long.class, tenantId, projectId, selectedEpisode);
        List<StoryboardResponse> storyboards = storyboardPage(tenantId, projectId, selectedEpisode,
            safePageSize, (safeCurrent - 1) * safePageSize);
        return new StoryboardWorkspacePageResponse(projectId,
            navigation.stream().map(ScriptEpisodeSummaryResponse::from).toList(), selectedEpisode, safeCurrent,
            safePageSize, total == null ? 0L : total, storyboards);
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
        Map<Long, ScriptAnalysisResultEntity> results = scriptAnalysisResultMapper.selectLatestByStageIds(
            stages.stream().map(ScriptAnalysisStageEntity::getId).toList());
        if (results == null) results = Map.of();
        Map<Long, Long> agentRuns = latestAgentRuns(stages);
        Map<Long, Map<String, Object>> fanoutSnapshots = latestFanoutSnapshots(stages);
        Map<Long, EpisodeFanoutProgressResponse> fanouts = new LinkedHashMap<>();
        Map<Long, EpisodeSplitProgressResponse> splitProgress = new LinkedHashMap<>();
        for (ScriptAnalysisStageEntity stage : stages) {
            EpisodeFanoutProgressResponse fanout = fanoutProgress(fanoutSnapshots.get(stage.getId()));
            if (fanout != null) fanouts.put(stage.getId(), fanout);
            if ("EPISODE_SPLITTING".equals(stage.getStageCode())) {
                splitProgress.put(stage.getId(), splitProgress(agentRuns.get(stage.getId())));
            }
        }
        return ScriptAnalysisTaskResponse.from(
            task, stages, results, agentRuns, fanouts, splitProgress,
            episodePipelineStatuses(task, stages, fanouts));
    }

    private Map<Long, Long> latestAgentRuns(List<ScriptAnalysisStageEntity> stages) {
        List<Long> stageIds = stages.stream().map(ScriptAnalysisStageEntity::getId).toList();
        if (stageIds.isEmpty()) return Map.of();
        String placeholders = stageIds.stream().map(item -> "?").collect(java.util.stream.Collectors.joining(", "));
        Map<Long, Long> latest = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
            select analysis_stage_id, id from ai_workflow_agent_run
             where analysis_stage_id in (%s)
             order by analysis_stage_id, created_at desc, id desc
            """.formatted(placeholders), stageIds.toArray()).forEach(row -> latest.putIfAbsent(
                longNumber(row.get("analysis_stage_id")), longNumber(row.get("id"))));
        return latest;
    }

    private Map<Long, Map<String, Object>> latestFanoutSnapshots(List<ScriptAnalysisStageEntity> stages) {
        List<Long> stageIds = stages.stream().map(ScriptAnalysisStageEntity::getId).toList();
        if (stageIds.isEmpty()) return Map.of();
        String placeholders = stageIds.stream().map(item -> "?").collect(java.util.stream.Collectors.joining(", "));
        Map<Long, Map<String, Object>> latest = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
            select id, stage_id, status, total_units, completed_units, failed_units, episode_set_hash
              from script_analysis_fanout_snapshot
             where stage_id in (%s)
             order by stage_id, attempt_no desc, id desc
            """.formatted(placeholders), stageIds.toArray()).forEach(row -> latest.putIfAbsent(
                longNumber(row.get("stage_id")), row));
        return latest;
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

    private EpisodeFanoutProgressResponse fanoutProgress(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
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
        CacheUsageResponse cache = fanoutCacheUsage(snapshotId);
        TimingUsageResponse timing = fanoutTimingUsage(snapshotId);
        return new EpisodeFanoutProgressResponse(
            snapshotId, status,
            ((Number) snapshot.get("total_units")).intValue(),
            ((Number) snapshot.get("completed_units")).intValue(),
            ((Number) snapshot.get("failed_units")).intValue(),
            current == null ? null : current.episodeId(),
            current == null ? null : current.episodeKey(),
            units.stream().anyMatch(unit -> "FAILED".equals(unit.status())
                || "PENDING".equals(unit.status()) || "STALE".equals(unit.status())),
            "STALE".equals(status), units, cache, timing);
    }

    private TimingUsageResponse fanoutTimingUsage(long snapshotId) {
        List<Map<String, Object>> executionRows = jdbcTemplate.queryForList("""
            select execution.created_at, execution.started_at
              from script_analysis_fanout_snapshot snapshot
              join script_analysis_task task on task.id = snapshot.task_id
              left join ai_execution_task execution on execution.id = task.execution_id
             where snapshot.id = ?
            """, snapshotId);
        Long queueMs = executionRows.isEmpty() ? null : millis(
            executionRows.get(0).get("created_at"), executionRows.get(0).get("started_at"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select unit.id, unit.started_at unit_started, unit.finished_at unit_finished,
                   min(case when step.step_type='MODEL' then step.started_at end) first_model_started,
                   max(case when step.step_type='MODEL' then step.finished_at end) last_model_finished,
                   sum(case when step.step_type='MODEL' then coalesce(log.duration_ms, 0) else 0 end) model_ms
              from script_analysis_fanout_unit unit
              left join ai_workflow_agent_run_step step on step.run_id = unit.child_run_id
              left join ai_call_log log on log.id = step.ai_call_log_id
             where unit.snapshot_id = ?
             group by unit.id, unit.started_at, unit.finished_at
            """, snapshotId);
        long preparation = 0L;
        long model = 0L;
        long validation = 0L;
        long total = 0L;
        boolean hasCompleted = false;
        for (Map<String, Object> row : rows) {
            Long unitTotal = millis(row.get("unit_started"), row.get("unit_finished"));
            if (unitTotal != null) {
                hasCompleted = true;
                total += unitTotal;
            }
            Long prep = millis(row.get("unit_started"), row.get("first_model_started"));
            if (prep != null) preparation += prep;
            model += longNumber(row.get("model_ms"));
            Long save = millis(row.get("last_model_finished"), row.get("unit_finished"));
            if (save != null) validation += save;
        }
        return new TimingUsageResponse(queueMs, rows.isEmpty() ? null : preparation,
            rows.isEmpty() ? null : model, rows.isEmpty() ? null : validation,
            hasCompleted ? total : null, null);
    }

    private Long millis(Object from, Object to) {
        java.time.LocalDateTime left = dateTime(from);
        java.time.LocalDateTime right = dateTime(to);
        return left == null || right == null ? null
            : Math.max(0L, java.time.Duration.between(left, right).toMillis());
    }

    private java.time.LocalDateTime dateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return value instanceof java.time.LocalDateTime dateTime ? dateTime : null;
    }

    private CacheUsageResponse fanoutCacheUsage(long snapshotId) {
        List<CacheUsageAggregator.Call> calls = jdbcTemplate.query("""
            select log.prompt_tokens, log.cached_input_tokens
              from script_analysis_fanout_unit unit
              join ai_workflow_agent_run_step step on step.run_id = unit.child_run_id
                   and step.step_type = 'MODEL'
              join ai_call_log log on log.id = step.ai_call_log_id
             where unit.snapshot_id = ?
            """, (row, index) -> new CacheUsageAggregator.Call(
                (Integer) row.getObject("prompt_tokens"),
                (Integer) row.getObject("cached_input_tokens")), snapshotId);
        return CacheUsageAggregator.aggregate(calls);
    }

    private List<EpisodePipelineStatusResponse> episodePipelineStatuses(
        ScriptAnalysisTaskEntity task,
        List<ScriptAnalysisStageEntity> stages,
        Map<Long, EpisodeFanoutProgressResponse> fanouts
    ) {
        Map<Long, EpisodeFanoutUnitResponse> summaries = fanoutUnits(stages, fanouts, "EPISODE_SUMMARY");
        Map<Long, EpisodeFanoutUnitResponse> recognitions = fanoutUnits(
            stages, fanouts, "CHARACTER_SCENE_RECOGNITION");
        Map<Long, Map<String, Object>> events = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
            select event.episode_id, event.status event_status, event.execution_id,
                   event.error_message event_error, execution.status execution_status
              from episode_auto_storyboard_event event
              left join ai_execution_task execution on execution.id = event.execution_id
             where event.tenant_id = ? and event.project_id = ? and event.script_id = ?
             order by event.id desc
            """, task.getTenantId(), task.getProjectId(), task.getScriptId()).forEach(row ->
                events.putIfAbsent(longNumber(row.get("episode_id")), row));
        Map<Long, Long> storyboards = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
            select episode_id, min(id) storyboard_id from storyboard
             where tenant_id = ? and project_id = ? and script_id = ? and deleted_at is null
             group by episode_id
            """, task.getTenantId(), task.getProjectId(), task.getScriptId()).forEach(row ->
                storyboards.put(longNumber(row.get("episode_id")), longNumber(row.get("storyboard_id"))));
        return jdbcTemplate.queryForList("""
            select id, stable_key, episode_no from script_episode
             where tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null order by episode_no, id
            """, task.getTenantId(), task.getProjectId(), task.getScriptId()).stream().map(row -> {
                long episodeId = longNumber(row.get("id"));
                EpisodeFanoutUnitResponse summary = summaries.get(episodeId);
                EpisodeFanoutUnitResponse recognition = recognitions.get(episodeId);
                Map<String, Object> event = events.get(episodeId);
                Long storyboardId = storyboards.get(episodeId);
                String eventStatus = event == null ? null : string(event.get("event_status"));
                String executionStatus = event == null ? null : string(event.get("execution_status"));
                String storyboardStatus = storyboardId != null ? "SUCCEEDED"
                    : executionStatus != null ? executionStatus : eventStatus;
                return new EpisodePipelineStatusResponse(
                    episodeId, string(row.get("stable_key")), number(row.get("episode_no")),
                    status(summary), runId(summary), error(summary),
                    status(recognition), runId(recognition), error(recognition),
                    storyboardStatus,
                    event == null ? null : nullableLong(event.get("execution_id")), storyboardId,
                    event == null ? null : string(event.get("event_error")), event != null,
                    "PROTECTED".equals(eventStatus));
            }).toList();
    }

    private Map<Long, EpisodeFanoutUnitResponse> fanoutUnits(
        List<ScriptAnalysisStageEntity> stages,
        Map<Long, EpisodeFanoutProgressResponse> fanouts,
        String stageCode
    ) {
        return stages.stream().filter(stage -> stageCode.equals(stage.getStageCode())).findFirst()
            .map(stage -> fanouts.get(stage.getId())).map(EpisodeFanoutProgressResponse::units)
            .orElse(List.of()).stream().collect(java.util.stream.Collectors.toMap(
                EpisodeFanoutUnitResponse::episodeId, unit -> unit, (left, right) -> right,
                LinkedHashMap::new));
    }

    private String status(EpisodeFanoutUnitResponse unit) { return unit == null ? "PENDING" : unit.status(); }
    private Long runId(EpisodeFanoutUnitResponse unit) { return unit == null ? null : unit.childRunId(); }
    private String error(EpisodeFanoutUnitResponse unit) { return unit == null ? null : unit.errorMessage(); }
    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private long longNumber(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
    private Long nullableLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }



    @Transactional
    public Void saveCurrent(Long tenantId, Long projectId, SaveScriptRequest request, HttpServletRequest servletRequest) {
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
        return null;
    }

    @Transactional
    public Void applyVersion(Long tenantId, Long projectId, Long versionId, HttpServletRequest servletRequest) {
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
        return null;
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
    public Void updateElement(Long tenantId, Long projectId, String elementType, Long elementId, UpdateScriptElementRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        switch (normalizeElementType(elementType)) {
            case "CHARACTER" -> jdbcTemplate.update("""
                update character_asset
                   set name = ?, role_type = ?, gender = ?, age_range = ?, identity = ?, personality = ?, appearance = ?, prompt = ?, status = ?, source = 'USER', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, request.name().trim(), defaultValue(request.roleType(), "SUPPORTING"), blankToNull(request.gender()), blankToNull(request.ageRange()), blankToNull(request.identity()), joinTags(request.personality()), blankToNull(request.appearance()), blankToNull(request.prompt()), normalizeStatus(request.status()), tenantId, projectId, elementId);
            case "SCENE" -> jdbcTemplate.update("""
                update scene_asset
                   set name = ?, scene_type = ?, time_atmosphere = ?, description = ?, visual_style = ?, prompt = ?, status = ?, source = 'USER', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, request.name().trim(), defaultValue(request.sceneType(), "INTERIOR"), blankToNull(request.atmosphere()), blankToNull(request.description()), blankToNull(request.visualStyle()), blankToNull(request.prompt()), normalizeStatus(request.status()), tenantId, projectId, elementId);
            case "PROP" -> jdbcTemplate.update("""
                update prop_asset
                   set name = ?, prop_type = ?, appearance = ?, plot_function = ?, related_character = ?, prompt = ?, status = ?, source = 'USER', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, request.name().trim(), defaultValue(request.propType(), "KEY_PROP"), blankToNull(request.appearance()), blankToNull(request.plotFunction()), blankToNull(request.relatedCharacter()), blankToNull(request.prompt()), normalizeStatus(request.status()), tenantId, projectId, elementId);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        }
        return null;
    }


    @Transactional
    public Void deleteElement(Long tenantId, Long projectId, String elementType, Long elementId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT", projectId);
        jdbcTemplate.update("""
            update %s set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """.formatted(elementTable(normalizeElementType(elementType))), tenantId, projectId, elementId);
        return null;
    }

    @Transactional
    public Void createStoryboard(Long tenantId, Long projectId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        Long episodeId = lockStoryboardEpisode(tenantId, projectId, script == null ? null : script.getId(), request.episodeNo());
        insertStoryboard(tenantId, projectId, script == null ? null : script.getId(), episodeId, context.userId(), request.episodeNo(), request.shotNo(), request.sceneNo(), request.shotType(), request.visualDescription(), request.characters(), request.actions(), request.dialogue(), request.scene(), request.props(), request.mood(), request.durationSeconds(), request.imagePrompt(), request.videoPrompt(), normalizeStatus(request.status()));
        if (request.storyboardNo() != null || request.promptDocument() != null) {
            jdbcTemplate.update("""
                update storyboard set storyboard_no = coalesce(?, shot_no), prompt_document_json = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = (
                   select id from (select id from storyboard where tenant_id = ? and project_id = ?
                     and deleted_at is null order by id desc limit 1) latest)
                """, request.storyboardNo(), writeJson(request.promptDocument()), tenantId, projectId,
                tenantId, projectId);
        }
        return null;
    }

    @Transactional
    public Void updateStoryboard(Long tenantId, Long projectId, Long storyboardId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        Long episodeId = lockStoryboardEpisode(tenantId, projectId, script == null ? null : script.getId(), request.episodeNo());
        jdbcTemplate.update("""
            update storyboard
               set episode_no = ?, episode_id = ?, shot_no = ?, storyboard_no = coalesce(?, storyboard_no, shot_no),
                   scene_no = ?, shot_type = ?, visual_description = ?, characters = ?, actions = ?,
                   dialogue = ?, scene = ?, props = ?, mood = ?, duration_seconds = ?, image_prompt = ?,
                   video_prompt = ?, prompt_document_json = coalesce(?, prompt_document_json), status = ?, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, request.episodeNo(), episodeId, request.shotNo(), request.storyboardNo(), blankToNull(request.sceneNo()),
            blankToNull(request.shotType()), request.visualDescription().trim(), blankToNull(request.characters()),
            blankToNull(request.actions()), blankToNull(request.dialogue()), blankToNull(request.scene()),
            blankToNull(request.props()), blankToNull(request.mood()), request.durationSeconds(),
            blankToNull(request.imagePrompt()), blankToNull(request.videoPrompt()), writeJson(request.promptDocument()),
            normalizeStatus(request.status()), tenantId, projectId, storyboardId);
        return null;
    }

    @Transactional
    public Void moveStoryboard(Long tenantId, Long projectId, Long storyboardId, MoveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard set shot_no = ?, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, request.shotNo(), tenantId, projectId, storyboardId);
        return null;
    }

    @Transactional
    public Void confirmStoryboards(Long tenantId, Long projectId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard set status = 'CONFIRMED', updated_at = now()
             where tenant_id = ? and project_id = ? and deleted_at is null
            """, tenantId, projectId);
        return null;
    }

    @Transactional
    public Void deleteStoryboard(Long tenantId, Long projectId, Long storyboardId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, tenantId, projectId, storyboardId);
        return null;
    }


    private PromptBackfillTarget promptBackfillTarget(Long tenantId, Long projectId, String targetType) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("instruction", "只返回 JSON。为以下缺少提示词的记录生成 prompt；角色使用角色设定图 Markdown 模板，场景使用静态四宫格模板，道具只描述本体，视觉形态只描述相对主体的增量。不得返回未列出的 id。每项格式为 {id,prompt}，分镜格式为 {id,imagePrompt,videoPrompt}。");
        request.put("targetType", targetType);
        int count = 0;
        if (includes(targetType, "CHARACTER")) {
            count += addMissingPromptRows(request, "characters", """
                select id, name, identity, appearance from character_asset
                 where tenant_id = ? and project_id = ? and deleted_at is null and (prompt is null or trim(prompt) = '')
                """, tenantId, projectId);
            count += addMissingVariantRows(request, "characterLooks", "CHARACTER", tenantId, projectId);
        }
        if (includes(targetType, "SCENE")) {
            count += addMissingPromptRows(request, "scenes", """
                select id, name, description, visual_style from scene_asset
                 where tenant_id = ? and project_id = ? and deleted_at is null and (prompt is null or trim(prompt) = '')
                """, tenantId, projectId);
            count += addMissingVariantRows(request, "sceneVariants", "SCENE", tenantId, projectId);
        }
        if (includes(targetType, "PROP")) {
            count += addMissingPromptRows(request, "props", """
                select id, name, appearance, plot_function from prop_asset
                 where tenant_id = ? and project_id = ? and deleted_at is null and (prompt is null or trim(prompt) = '')
                """, tenantId, projectId);
            count += addMissingVariantRows(request, "propVariants", "PROP", tenantId, projectId);
        }
        if (includes(targetType, "STORYBOARD")) {
            count += addMissingStoryboardRows(request, tenantId, projectId);
        }
        return new PromptBackfillTarget(count == 0, request.toString());
    }

    private int addMissingPromptRows(ObjectNode request, String field, String sql, Long tenantId, Long projectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, projectId);
        request.set(field, objectMapper.valueToTree(rows));
        return rows.size();
    }

    private int addMissingVariantRows(ObjectNode request, String field, String assetType, Long tenantId, Long projectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select id, asset_id as assetId, name, appearance from asset_visual_variant
             where tenant_id = ? and project_id = ? and asset_type = ? and deleted_at is null
               and is_primary = false
               and (prompt is null or trim(prompt) = '')
            """, tenantId, projectId, assetType);
        request.set(field, objectMapper.valueToTree(rows));
        return rows.size();
    }

    private int addMissingStoryboardRows(ObjectNode request, Long tenantId, Long projectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select id, visual_description as visualDescription, actions
              from storyboard where tenant_id = ? and project_id = ? and deleted_at is null
               and ((image_prompt is null or trim(image_prompt) = '') or (video_prompt is null or trim(video_prompt) = ''))
            """, tenantId, projectId);
        request.set("storyboards", objectMapper.valueToTree(rows));
        return rows.size();
    }

    private void applyGeneratedPrompts(Long tenantId, Long projectId, String targetType, String content) {
        JsonNode response = parsePromptBackfillResponse(content);
        if (includes(targetType, "CHARACTER")) {
            applyPromptRows(response.path("characters"), "character_asset", tenantId, projectId);
            applyVariantPromptRows(response.path("characterLooks"), "CHARACTER", tenantId, projectId);
        }
        if (includes(targetType, "SCENE")) {
            applyPromptRows(response.path("scenes"), "scene_asset", tenantId, projectId);
            applyVariantPromptRows(response.path("sceneVariants"), "SCENE", tenantId, projectId);
        }
        if (includes(targetType, "PROP")) {
            applyPromptRows(response.path("props"), "prop_asset", tenantId, projectId);
            applyVariantPromptRows(response.path("propVariants"), "PROP", tenantId, projectId);
        }
        if (includes(targetType, "STORYBOARD")) applyStoryboardPromptRows(response.path("storyboards"), tenantId, projectId);
    }

    static JsonNode parsePromptBackfillResponse(String content) {
        if (content == null || content.isBlank()) throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "提示词生成未返回内容。");
        String json = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try {
            JsonNode parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (!parsed.isObject()) throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "提示词生成结果必须是 JSON 对象。");
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "提示词生成结果不是有效 JSON。");
        }
    }

    private void applyPromptRows(JsonNode rows, String table, Long tenantId, Long projectId) {
        for (JsonNode row : rows) updatePromptIfEmpty(table, row.path("id").asLong(), row.path("prompt").asText(), tenantId, projectId);
    }

    private void applyVariantPromptRows(JsonNode rows, String assetType, Long tenantId, Long projectId) {
        for (JsonNode row : rows) {
            String prompt = row.path("prompt").asText();
            if (!prompt.isBlank() && row.path("id").canConvertToLong()) jdbcTemplate.update("""
                update asset_visual_variant set prompt = ?, updated_at = now()
                 where id = ? and tenant_id = ? and project_id = ? and asset_type = ? and deleted_at is null
                   and (prompt is null or trim(prompt) = '')
                """, prompt, row.path("id").asLong(), tenantId, projectId, assetType);
        }
    }

    private void updatePromptIfEmpty(String table, long id, String prompt, Long tenantId, Long projectId) {
        if (!prompt.isBlank() && id > 0) jdbcTemplate.update("update " + table + " set prompt = ?, updated_at = now() where id = ? and tenant_id = ? and project_id = ? and deleted_at is null and (prompt is null or trim(prompt) = '')", prompt, id, tenantId, projectId);
    }

    private void applyStoryboardPromptRows(JsonNode rows, Long tenantId, Long projectId) {
        for (JsonNode row : rows) {
            long id = row.path("id").asLong();
            updateStoryboardPromptIfEmpty("image_prompt", id, row.path("imagePrompt").asText(), tenantId, projectId);
            updateStoryboardPromptIfEmpty("video_prompt", id, row.path("videoPrompt").asText(), tenantId, projectId);
        }
    }

    private void updateStoryboardPromptIfEmpty(String column, long id, String prompt, Long tenantId, Long projectId) {
        if (!prompt.isBlank() && id > 0) jdbcTemplate.update("update storyboard set " + column + " = ?, updated_at = now() where id = ? and tenant_id = ? and project_id = ? and deleted_at is null and (" + column + " is null or trim(" + column + ") = '')", prompt, id, tenantId, projectId);
    }

    private boolean includes(String targetType, String type) {
        return "ALL".equals(targetType) || type.equals(targetType);
    }

    private record PromptBackfillTarget(boolean empty, String request) {}

    private ProjectEntity requireProjectAccess(TenantContext context, Long projectId) {
        return requireProjectAccessContext(context, projectId).project();
    }

    private ProjectAccessContext requireProjectAccessContext(TenantContext context, Long projectId) {
        return projectAccessResolver.requireView(context.tenantId(), projectId);
    }

    private void requirePermission(TenantContext context, String permissionCode, Long projectId) {
        projectPermissionGuard.require(context.tenantId(), projectId, permissionCode);
    }

    private void requirePermission(ProjectAccessContext access, String permissionCode) {
        projectPermissionGuard.require(access, permissionCode);
    }




    private AssetVisualWorkspace buildAssetVisualWorkspace(
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
        EpisodeAwareVisualResolver.ResolvedVisual resolved =
            episodeAwareVisualResolver.resolve(tenantId, projectId, assetType, assetId, null);
        return new AssetVisualWorkspace(variants.size(), primary, variants, generationSummary,
            assetVisualBindingService.list(tenantId, projectId, assetType, assetId),
            resolved.imageUrl(), resolved.source());
    }


    private List<CharacterAssetSummaryResponse> characterSummaries(Long tenantId, Long projectId, Long scriptId) {
        return jdbcTemplate.query("""
            select asset.id, asset.name, asset.role_type, asset.gender, asset.age_range, asset.identity,
                   asset.personality, asset.appearance, asset.prompt, asset.status, asset.merge_target_id,
                   asset.main_image_url, asset.main_image_result_id
              from character_asset asset
             where asset.tenant_id = ? and asset.project_id = ? and asset.deleted_at is null
               and (asset.script_id = ? or asset.script_id is null)
             order by asset.id
            """, (rs, rowNum) -> new CharacterAssetSummaryResponse(rs.getLong("id"), rs.getString("name"),
                rs.getString("role_type"), rs.getString("gender"), rs.getString("age_range"),
                rs.getString("identity"), splitTags(rs.getString("personality")), rs.getString("appearance"),
                rs.getString("prompt"), rs.getString("status"), rs.getObject("merge_target_id", Long.class),
                materialFileAccessService.publicUrl(rs.getString("main_image_url")),
                mainImageThumbnailUrl(projectId, rs.getObject("main_image_result_id", Long.class))),
            tenantId, projectId, scriptId);
    }

    private List<SceneAssetSummaryResponse> sceneSummaries(Long tenantId, Long projectId, Long scriptId) {
        return jdbcTemplate.query("""
            select asset.id, asset.name, asset.scene_type, asset.time_atmosphere, asset.description,
                   asset.visual_style, asset.prompt, asset.status, asset.merge_target_id, asset.main_image_url,
                   asset.main_image_result_id
              from scene_asset asset
             where asset.tenant_id = ? and asset.project_id = ? and asset.deleted_at is null
               and (asset.script_id = ? or asset.script_id is null)
             order by asset.id
            """, (rs, rowNum) -> new SceneAssetSummaryResponse(rs.getLong("id"), rs.getString("name"),
                rs.getString("scene_type"), rs.getString("time_atmosphere"), rs.getString("description"),
                rs.getString("visual_style"), rs.getString("prompt"), rs.getString("status"),
                rs.getObject("merge_target_id", Long.class), materialFileAccessService.publicUrl(rs.getString("main_image_url")),
                mainImageThumbnailUrl(projectId, rs.getObject("main_image_result_id", Long.class))),
            tenantId, projectId, scriptId);
    }

    private List<PropAssetSummaryResponse> propSummaries(Long tenantId, Long projectId, Long scriptId) {
        return jdbcTemplate.query("""
            select asset.id, asset.name, asset.prop_type, asset.appearance, asset.plot_function, asset.prompt,
                   asset.status, asset.merge_target_id, asset.main_image_url, asset.main_image_result_id
              from prop_asset asset
             where asset.tenant_id = ? and asset.project_id = ? and asset.deleted_at is null
               and (asset.script_id = ? or asset.script_id is null)
             order by asset.id
            """, (rs, rowNum) -> new PropAssetSummaryResponse(rs.getLong("id"), rs.getString("name"),
                rs.getString("prop_type"), rs.getString("appearance"), rs.getString("plot_function"),
                rs.getString("prompt"), rs.getString("status"), rs.getObject("merge_target_id", Long.class),
                materialFileAccessService.publicUrl(rs.getString("main_image_url")),
                mainImageThumbnailUrl(projectId, rs.getObject("main_image_result_id", Long.class))),
            tenantId, projectId, scriptId);
    }

    private String mainImageThumbnailUrl(Long projectId, Long resultId) {
        return resultId == null ? null
            : "/api/projects/%d/ai-image-results/%d/thumbnail".formatted(projectId, resultId);
    }

    private List<StoryboardResponse> storyboardPage(Long tenantId, Long projectId, int episodeNo, int limit, int offset) {
        return jdbcTemplate.query("""
            select id, shot_no, coalesce(storyboard_no, shot_no) storyboard_no, episode_id, episode_no,
                   shot_type, visual_description, characters, scene, dialogue, duration_seconds, shot_plan_json,
                   prompt_document_json, material_binding_status, source_fingerprint, generated_by_run_id,
                   image_prompt, video_prompt, first_frame_url, current_video_result_id, current_video_url
              from storyboard where tenant_id = ? and project_id = ? and episode_no = ? and deleted_at is null
             order by shot_no, id limit ? offset ?
            """, (rs, rowNum) -> new StoryboardResponse(rs.getLong("id"), rs.getInt("shot_no"),
                rs.getInt("storyboard_no"), rs.getObject("episode_id", Long.class), rs.getInt("episode_no"),
                rs.getString("shot_type"), rs.getString("visual_description"), rs.getString("characters"),
                rs.getString("scene"), rs.getString("dialogue"), rs.getObject("duration_seconds", Integer.class),
                readJson(rs.getString("shot_plan_json")), readJson(rs.getString("prompt_document_json")),
                rs.getString("material_binding_status"), rs.getString("source_fingerprint"),
                rs.getObject("generated_by_run_id", Long.class), rs.getString("image_prompt"),
                rs.getString("video_prompt"), materialFileAccessService.publicUrl(rs.getString("first_frame_url")),
                rs.getObject("current_video_result_id", Long.class),
                materialFileAccessService.publicUrl(rs.getString("current_video_url"))), tenantId, projectId, episodeNo, limit, offset);
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
        Long episodeId,
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
              (tenant_id, project_id, script_id, episode_id, episode_no, shot_no, scene_no, shot_type, visual_description, characters, actions, dialogue, scene, props, mood, duration_seconds, image_prompt, video_prompt, status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
            tenantId,
            projectId,
            scriptId,
            episodeId,
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

    private Long lockStoryboardEpisode(Long tenantId, Long projectId, Long scriptId, Integer episodeNo) {
        if (scriptId == null) return null;
        List<Long> ids = jdbcTemplate.queryForList("""
            select id from script_episode
             where tenant_id=? and project_id=? and script_id=? and episode_no=?
               and status='ACTIVE' and retired_at is null order by id desc limit 1 for update
            """, Long.class, tenantId, projectId, scriptId, episodeNo == null ? 1 : episodeNo);
        return ids.isEmpty() ? null : ids.get(0);
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

    private AssetRecognitionScope parseAssetScope(String targetType) {
        try {
            return AssetRecognitionScope.valueOf(targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择 ALL、CHARACTER、SCENE 或 PROP 资产范围。");
        }
    }

    private AssetPromptPolicy parseAssetPromptPolicy(String promptPolicy) {
        try {
            return AssetPromptPolicy.valueOf(promptPolicy == null ? "" : promptPolicy.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "提示词策略必须为 FILL_EMPTY 或 REGENERATE_ALL。");
        }
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
