package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptWorkflowService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final RbacPermissionService rbacPermissionService;
    private final TenantContextResolver tenantContextResolver;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ScriptAnalysisTaskMapper scriptAnalysisTaskMapper;
    private final ScriptAnalysisStageMapper scriptAnalysisStageMapper;
    private final ScriptAnalysisResultMapper scriptAnalysisResultMapper;
    private final ScriptAnalysisTaskService scriptAnalysisTaskService;
    private final ProjectAiConfigService projectAiConfigService;
    private final AiInvocationService aiInvocationService;
    private final MaterialFileAccessService materialFileAccessService;
    private final TeamPointService teamPointService;
    private final ScriptElementExtractionService scriptElementExtractionService;
    private final ScriptElementDraftService scriptElementDraftService;
    private final ScriptElementConfirmationService scriptElementConfirmationService;
    private final JdbcTemplate jdbcTemplate;

    public ScriptWorkflowService(
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        RbacPermissionService rbacPermissionService,
        TenantContextResolver tenantContextResolver,
        ScriptMapper scriptMapper,
        ScriptVersionMapper scriptVersionMapper,
        ScriptAnalysisTaskMapper scriptAnalysisTaskMapper,
        ScriptAnalysisStageMapper scriptAnalysisStageMapper,
        ScriptAnalysisResultMapper scriptAnalysisResultMapper,
        ScriptAnalysisTaskService scriptAnalysisTaskService,
        ProjectAiConfigService projectAiConfigService,
        AiInvocationService aiInvocationService,
        MaterialFileAccessService materialFileAccessService,
        TeamPointService teamPointService,
        ScriptElementExtractionService scriptElementExtractionService,
        ScriptElementDraftService scriptElementDraftService,
        ScriptElementConfirmationService scriptElementConfirmationService,
        JdbcTemplate jdbcTemplate
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.rbacPermissionService = rbacPermissionService;
        this.tenantContextResolver = tenantContextResolver;
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.scriptAnalysisTaskMapper = scriptAnalysisTaskMapper;
        this.scriptAnalysisStageMapper = scriptAnalysisStageMapper;
        this.scriptAnalysisResultMapper = scriptAnalysisResultMapper;
        this.scriptAnalysisTaskService = scriptAnalysisTaskService;
        this.projectAiConfigService = projectAiConfigService;
        this.aiInvocationService = aiInvocationService;
        this.materialFileAccessService = materialFileAccessService;
        this.teamPointService = teamPointService;
        this.scriptElementExtractionService = scriptElementExtractionService;
        this.scriptElementDraftService = scriptElementDraftService;
        this.scriptElementConfirmationService = scriptElementConfirmationService;
        this.jdbcTemplate = jdbcTemplate;
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
        return new ScriptWorkspaceResponse(
            projectId,
            ScriptResponse.from(script),
            versions,
            characters(tenantId, projectId),
            scenes(tenantId, projectId),
            props(tenantId, projectId),
            storyboards(tenantId, projectId),
            ScriptEpisodeParser.parse(script == null ? null : script.getContent()),
            analysis(tenantId, projectId, script)
        );
    }

    public ScriptAnalysisTaskResponse currentAnalysis(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, project.id);
        return analysis(tenantId, projectId, script);
    }

    @Transactional
    public ScriptAnalysisTaskResponse retryAnalysis(Long tenantId, Long projectId, String stageCode) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        ScriptAnalysisTaskEntity task = scriptAnalysisTaskService.retryStage(tenantId, projectId, stageCode);
        return analysisResponse(task);
    }

    @Transactional
    public ScriptAnalysisTaskResponse reanalyze(Long tenantId, Long projectId) {
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
    public ScriptAnalysisTaskResponse reanalyzeVersion(Long tenantId, Long projectId, Long versionId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "AI_SERVICE:USE", projectId);
        return reanalyzeVersion(tenantId, projectId, versionId, context);
    }

    private ScriptAnalysisTaskResponse reanalyzeVersion(
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
        return analysisResponse(task);
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
        for (ScriptAnalysisStageEntity stage : stages) {
            ScriptAnalysisResultEntity result = scriptAnalysisResultMapper.selectLatestByStage(stage.getId());
            if (result != null) {
                results.put(stage.getId(), result);
            }
        }
        return ScriptAnalysisTaskResponse.from(task, stages, results);
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
        version.setStatus("APPLIED");
        scriptVersionMapper.updateById(version);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse extractElements(Long tenantId, Long projectId, ExtractScriptElementsRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:AI_EXTRACT", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null || script.getContent() == null || script.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先生成或填写剧本内容。");
        }
        ScriptElementType elementType = ScriptElementType.from(request.elementType());
        ScriptElementExtractionResult result = scriptElementExtractionService.extract(context, projectId, script, elementType);
        scriptElementDraftService.replaceDrafts(tenantId, projectId, context.userId(), result);
        return workspace(tenantId, projectId);
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
        ScriptEntity script = requireScript(tenantId, projectId);
        jdbcTemplate.update("""
            update storyboard set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and status = 'DRAFT' and deleted_at is null
            """, tenantId, projectId);
        insertStoryboard(tenantId, projectId, script.getId(), context.userId(), 1, 1, "场景一", "远景", "雨夜中，林家老宅大门外亮起冷光，主角拖着行李箱出现。", "主角", "主角停在门前，抬头看向门匾。", "三年前你们把我赶出去，今天我回来。", "林家老宅门口", "行李箱", "压抑、回归", 5, "首帧：雨夜豪门老宅门口，主角拖行李箱，冷色电影感", "竖屏短剧镜头，雨水落下，镜头缓慢推进", "DRAFT");
        insertStoryboard(tenantId, projectId, script.getId(), context.userId(), 1, 2, "场景二", "中景", "宴会厅内笑声戛然而止，宾客同时回头。", "主角、旧日熟人", "旧日熟人后退半步，表情震惊。", "这不可能，她怎么会回来？", "宴会厅", "香槟杯", "震惊、反转", 6, "首帧：豪门宴会厅众人回头，主角站在入口", "竖屏短剧镜头，人群视线聚焦，轻微推拉", "DRAFT");
        insertStoryboard(tenantId, projectId, script.getId(), context.userId(), 1, 3, "场景二", "特写", "旧股权协议末页的签名被主角按在灯光下。", "主角", "主角将协议推到桌面中央。", "属于我的，我一分都不会让。", "宴会厅", "旧股权协议", "强冲突、悬念", 4, "首帧：旧股权协议签名特写，手指压住纸张", "竖屏短剧镜头，特写切入，灯光扫过签名", "DRAFT");
        callTextInvocation(context, projectId, AiBusinessScene.STORYBOARD_BREAKDOWN, script.getTitle(), "拆解分镜成功");
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse createStoryboard(Long tenantId, Long projectId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        insertStoryboard(tenantId, projectId, script == null ? null : script.getId(), context.userId(), request.episodeNo(), request.shotNo(), request.sceneNo(), request.shotType(), request.visualDescription(), request.characters(), request.actions(), request.dialogue(), request.scene(), request.props(), request.mood(), request.durationSeconds(), request.imagePrompt(), request.videoPrompt(), normalizeStatus(request.status()));
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse updateStoryboard(Long tenantId, Long projectId, Long storyboardId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT", projectId);
        jdbcTemplate.update("""
            update storyboard
               set episode_no = ?, shot_no = ?, scene_no = ?, shot_type = ?, visual_description = ?, characters = ?, actions = ?, dialogue = ?, scene = ?, props = ?, mood = ?, duration_seconds = ?, image_prompt = ?, video_prompt = ?, status = ?, updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, request.episodeNo(), request.shotNo(), blankToNull(request.sceneNo()), blankToNull(request.shotType()), request.visualDescription().trim(), blankToNull(request.characters()), blankToNull(request.actions()), blankToNull(request.dialogue()), blankToNull(request.scene()), blankToNull(request.props()), blankToNull(request.mood()), request.durationSeconds(), blankToNull(request.imagePrompt()), blankToNull(request.videoPrompt()), normalizeStatus(request.status()), tenantId, projectId, storyboardId);
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
        callTextInvocation(context, projectId, AiBusinessScene.PROMPT_GENERATE, targetType, "生成提示词成功");
        return workspace(tenantId, projectId);
    }

    private ProjectEntity requireProjectAccess(TenantContext context, Long projectId) {
        ProjectEntity project = projectMapper.selectByTenantIdAndId(context.tenantId(), projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        if (rbacPermissionService.hasPermission(context, "PROJECT:VIEW")) {
            return project;
        }
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(context.tenantId(), projectId, context.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目。");
        }
        return project;
    }

    private void requirePermission(TenantContext context, String permissionCode, Long projectId) {
        if (!rbacPermissionService.hasPermission(context, permissionCode, projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行该操作。");
        }
    }

    private List<CharacterAssetResponse> characters(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, name, role_type, gender, age_range, identity, personality, appearance, prompt, status, merge_target_id
              from character_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
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
                rs.getObject("merge_target_id", Long.class)
            ), tenantId, projectId);
    }

    private List<SceneAssetResponse> scenes(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, name, scene_type, time_atmosphere, description, visual_style, prompt, status, merge_target_id
              from scene_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
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
                rs.getObject("merge_target_id", Long.class)
            ), tenantId, projectId);
    }

    private List<PropAssetResponse> props(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, name, prop_type, appearance, plot_function, prompt, status, merge_target_id
              from prop_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
             order by id
            """, (rs, rowNum) -> new PropAssetResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("prop_type"),
                rs.getString("appearance"),
                rs.getString("plot_function"),
                rs.getString("prompt"),
                rs.getString("status"),
                rs.getObject("merge_target_id", Long.class)
            ), tenantId, projectId);
    }

    private List<StoryboardResponse> storyboards(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, shot_no, episode_no, shot_type, visual_description, characters, scene, dialogue, duration_seconds, image_prompt, video_prompt, first_frame_url, current_video_result_id, current_video_url
              from storyboard
             where tenant_id = ? and project_id = ? and deleted_at is null
             order by episode_no, shot_no, id
            """, (rs, rowNum) -> new StoryboardResponse(
                rs.getLong("id"),
                rs.getInt("shot_no"),
                rs.getInt("episode_no"),
                rs.getString("shot_type"),
                rs.getString("visual_description"),
                rs.getString("characters"),
                rs.getString("scene"),
                rs.getString("dialogue"),
                rs.getObject("duration_seconds", Integer.class),
                rs.getString("image_prompt"),
                rs.getString("video_prompt"),
                materialFileAccessService.publicUrl(rs.getString("first_frame_url")),
                rs.getObject("current_video_result_id", Long.class),
                materialFileAccessService.publicUrl(rs.getString("current_video_url"))
            ), tenantId, projectId);
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
        teamPointService.consumeForAi(context, 1, scene.pointScene(), null, "AI 调用消耗积分");
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
        teamPointService.consumeForAi(context, 1, scene.pointScene(), null, "AI 调用消耗积分");
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
        return version;
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
