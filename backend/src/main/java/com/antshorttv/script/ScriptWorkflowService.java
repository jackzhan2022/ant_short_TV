package com.antshorttv.script;

import com.antshorttv.ai.AiServiceConfigEntity;
import com.antshorttv.ai.AiServiceConfigMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final JdbcTemplate jdbcTemplate;

    public ScriptWorkflowService(
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        RbacPermissionService rbacPermissionService,
        TenantContextResolver tenantContextResolver,
        ScriptMapper scriptMapper,
        ScriptVersionMapper scriptVersionMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        JdbcTemplate jdbcTemplate
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.rbacPermissionService = rbacPermissionService;
        this.tenantContextResolver = tenantContextResolver;
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
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
            storyboards(tenantId, projectId)
        );
    }

    @Transactional
    public ScriptWorkspaceResponse generate(Long tenantId, Long projectId, GenerateScriptRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
        requirePermission(context, "SCRIPT:AI_GENERATE");
        AiServiceConfigEntity config = resolveTextService(tenantId);
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
        String content = buildScriptContent(title, request);
        Long callLogId = recordAiCall(context, config, "script_generate", request.storyIdea(), "生成剧本草稿成功");
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
        requirePermission(context, "SCRIPT:AI_REWRITE");
        AiServiceConfigEntity config = resolveTextService(tenantId);
        ScriptEntity script = requireScript(tenantId, projectId);
        String type = request.rewriteType().trim();
        String requirement = blankToNull(request.requirement());
        String content = """
            %s

            【AI改写版本】
            改写类型：%s
            改写要求：%s
            改写说明：已将冲突前置、对白缩短，并为每一场保留明确镜头钩子。
            风险提示：请确认角色关系和关键伏笔是否与正式设定一致。
            """.formatted(script.getContent(), type, requirement == null ? "保持原剧情核心" : requirement);
        Long callLogId = recordAiCall(context, config, "script_rewrite", type, "改写剧本成功");
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
        requirePermission(context, "SCRIPT:EDIT");
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
        requirePermission(context, "SCRIPT:EDIT");
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
        requirePermission(context, "ELEMENT:AI_EXTRACT");
        AiServiceConfigEntity config = resolveTextService(tenantId);
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null || script.getContent() == null || script.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先生成或填写剧本内容。");
        }
        String elementType = request.elementType().trim();
        switch (elementType) {
            case "CHARACTER" -> {
                ensureCharacters(tenantId, projectId, context.userId(), script.getContent());
                recordAiCall(context, config, "character_extract", script.getTitle(), "提取角色成功");
            }
            case "SCENE" -> {
                ensureScenes(tenantId, projectId, context.userId(), script.getContent());
                recordAiCall(context, config, "scene_extract", script.getTitle(), "提取场景成功");
            }
            case "PROP" -> {
                ensureProps(tenantId, projectId, context.userId(), script.getContent());
                recordAiCall(context, config, "prop_extract", script.getTitle(), "提取道具成功");
            }
            case "ALL" -> {
                ensureCharacters(tenantId, projectId, context.userId(), script.getContent());
                ensureScenes(tenantId, projectId, context.userId(), script.getContent());
                ensureProps(tenantId, projectId, context.userId(), script.getContent());
                recordAiCall(context, config, "character_extract", script.getTitle(), "提取角色成功");
                recordAiCall(context, config, "scene_extract", script.getTitle(), "提取场景成功");
                recordAiCall(context, config, "prop_extract", script.getTitle(), "提取道具成功");
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择要提取的元素类型。");
        }
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse updateElement(Long tenantId, Long projectId, String elementType, Long elementId, UpdateScriptElementRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT");
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
        requirePermission(context, "ELEMENT:EDIT");
        jdbcTemplate.update("""
            update %s set status = 'CONFIRMED', updated_at = now()
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """.formatted(elementTable(normalizeElementType(elementType))), tenantId, projectId, elementId);
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse deleteElement(Long tenantId, Long projectId, String elementType, Long elementId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "ELEMENT:EDIT");
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
        requirePermission(context, "STORYBOARD:AI_BREAKDOWN");
        AiServiceConfigEntity config = resolveTextService(tenantId);
        ScriptEntity script = requireScript(tenantId, projectId);
        jdbcTemplate.update("""
            update storyboard set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and status = 'DRAFT' and deleted_at is null
            """, tenantId, projectId);
        insertStoryboard(tenantId, projectId, script.getId(), context.userId(), 1, 1, "场景一", "远景", "雨夜中，林家老宅大门外亮起冷光，主角拖着行李箱出现。", "主角", "主角停在门前，抬头看向门匾。", "三年前你们把我赶出去，今天我回来。", "林家老宅门口", "行李箱", "压抑、回归", 5, "首帧：雨夜豪门老宅门口，主角拖行李箱，冷色电影感", "竖屏短剧镜头，雨水落下，镜头缓慢推进", "DRAFT");
        insertStoryboard(tenantId, projectId, script.getId(), context.userId(), 1, 2, "场景二", "中景", "宴会厅内笑声戛然而止，宾客同时回头。", "主角、旧日熟人", "旧日熟人后退半步，表情震惊。", "这不可能，她怎么会回来？", "宴会厅", "香槟杯", "震惊、反转", 6, "首帧：豪门宴会厅众人回头，主角站在入口", "竖屏短剧镜头，人群视线聚焦，轻微推拉", "DRAFT");
        insertStoryboard(tenantId, projectId, script.getId(), context.userId(), 1, 3, "场景二", "特写", "旧股权协议末页的签名被主角按在灯光下。", "主角", "主角将协议推到桌面中央。", "属于我的，我一分都不会让。", "宴会厅", "旧股权协议", "强冲突、悬念", 4, "首帧：旧股权协议签名特写，手指压住纸张", "竖屏短剧镜头，特写切入，灯光扫过签名", "DRAFT");
        recordAiCall(context, config, "storyboard_breakdown", script.getTitle(), "拆解分镜成功");
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse createStoryboard(Long tenantId, Long projectId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT");
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        insertStoryboard(tenantId, projectId, script == null ? null : script.getId(), context.userId(), request.episodeNo(), request.shotNo(), request.sceneNo(), request.shotType(), request.visualDescription(), request.characters(), request.actions(), request.dialogue(), request.scene(), request.props(), request.mood(), request.durationSeconds(), request.imagePrompt(), request.videoPrompt(), normalizeStatus(request.status()));
        return workspace(tenantId, projectId);
    }

    @Transactional
    public ScriptWorkspaceResponse updateStoryboard(Long tenantId, Long projectId, Long storyboardId, SaveStoryboardRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requirePermission(context, "STORYBOARD:EDIT");
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
        requirePermission(context, "STORYBOARD:EDIT");
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
        requirePermission(context, "STORYBOARD:EDIT");
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
        requirePermission(context, "STORYBOARD:EDIT");
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
        requirePermission(context, "PROMPT:AI_GENERATE");
        AiServiceConfigEntity config = resolveTextService(tenantId);
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
        recordAiCall(context, config, "prompt_generate", targetType, "生成提示词成功");
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

    private void requirePermission(TenantContext context, String permissionCode) {
        if (!rbacPermissionService.hasPermission(context, permissionCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行该操作。");
        }
    }

    private List<CharacterAssetResponse> characters(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, name, role_type, gender, age_range, identity, personality, appearance, prompt
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
                rs.getString("prompt")
            ), tenantId, projectId);
    }

    private List<SceneAssetResponse> scenes(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, name, scene_type, time_atmosphere, description, visual_style, prompt
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
                rs.getString("prompt")
            ), tenantId, projectId);
    }

    private List<PropAssetResponse> props(Long tenantId, Long projectId) {
        return jdbcTemplate.query("""
            select id, name, prop_type, appearance, plot_function, prompt
              from prop_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
             order by id
            """, (rs, rowNum) -> new PropAssetResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("prop_type"),
                rs.getString("appearance"),
                rs.getString("plot_function"),
                rs.getString("prompt")
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
                rs.getString("first_frame_url"),
                rs.getObject("current_video_result_id", Long.class),
                rs.getString("current_video_url")
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

    private void ensureCharacters(Long tenantId, Long projectId, Long userId, String scriptContent) {
        if (!characters(tenantId, projectId).isEmpty()) {
            return;
        }
        insertCharacter(tenantId, projectId, userId, "主角", "LEAD", "女", "25-30", "落魄千金", "坚韧、果断、有复仇目标", "雨夜拖着行李箱回归，眼神坚定", "短剧女主定妆照，落魄千金回归，雨夜，坚韧眼神");
        insertCharacter(tenantId, projectId, userId, "旧日熟人", "SUPPORTING", "未知", "30-40", "豪门旧识", "谨慎、震惊、知道旧案线索", "宴会厅人群后方认出主角", "短剧配角，豪门宴会，震惊表情，藏有秘密");
    }

    private void insertCharacter(Long tenantId, Long projectId, Long userId, String name, String roleType, String gender, String ageRange, String identity, String personality, String appearance, String prompt) {
        jdbcTemplate.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, gender, age_range, identity, personality, appearance, relationship_text, plot_function, prompt, status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, null, ?, 'DRAFT', ?, now(), now())
            """, tenantId, projectId, name, roleType, gender, ageRange, identity, personality, appearance, prompt, userId);
    }

    private void ensureScenes(Long tenantId, Long projectId, Long userId, String scriptContent) {
        if (!scenes(tenantId, projectId).isEmpty()) {
            return;
        }
        insertScene(tenantId, projectId, userId, "林家老宅门口", "EXTERIOR", "雨夜、压抑、回归", "豪门铁门外，雨水和冷光强化主角回归的压迫感", "竖屏短剧，冷色雨夜，豪门大门，电影感", "雨夜豪门老宅门口，铁门，主角拖着行李箱，冷色电影光");
        insertScene(tenantId, projectId, userId, "宴会厅", "INTERIOR", "热闹后骤然安静", "宾客聚集的豪门宴会空间，适合制造身份反转", "暖色宴会灯光，高级酒店大厅，人物视线聚焦", "豪门宴会厅，宾客震惊，灯光华丽，短剧反转场景");
    }

    private void insertScene(Long tenantId, Long projectId, Long userId, String name, String sceneType, String atmosphere, String description, String visualStyle, String prompt) {
        jdbcTemplate.update("""
            insert into scene_asset
              (tenant_id, project_id, name, scene_type, time_atmosphere, description, visual_style, plot_reference, prompt, status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, null, ?, 'DRAFT', ?, now(), now())
            """, tenantId, projectId, name, sceneType, atmosphere, description, visualStyle, prompt, userId);
    }

    private void ensureProps(Long tenantId, Long projectId, Long userId, String scriptContent) {
        if (!props(tenantId, projectId).isEmpty()) {
            return;
        }
        jdbcTemplate.update("""
            insert into prop_asset
              (tenant_id, project_id, name, prop_type, appearance, plot_function, related_character, prompt, status, created_by, created_at, updated_at)
            values (?, ?, '旧股权协议', 'DOCUMENT', '带旧签名的纸质协议，边角泛黄', '揭开主角回归和身份反转的关键证据', '主角', '短剧关键道具，旧股权协议，纸张特写，签名清晰', 'DRAFT', ?, now(), now())
            """, tenantId, projectId, userId);
    }

    private ScriptEntity requireScript(Long tenantId, Long projectId) {
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null || script.getContent() == null || script.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前项目暂无可用剧本。");
        }
        return script;
    }

    private AiServiceConfigEntity resolveTextService(Long tenantId) {
        AiServiceConfigEntity config = aiServiceConfigMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigEntity>()
            .eq(AiServiceConfigEntity::getTenantId, tenantId)
            .eq(AiServiceConfigEntity::getServiceType, "TEXT")
            .eq(AiServiceConfigEntity::getEnabled, true)
            .isNull(AiServiceConfigEntity::getDeletedAt)
            .orderByDesc(AiServiceConfigEntity::getIsDefault)
            .orderByDesc(AiServiceConfigEntity::getPriority)
            .last("limit 1"));
        if (config == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前团队未配置可用文本服务。");
        }
        return config;
    }

    private Long recordAiCall(TenantContext context, AiServiceConfigEntity config, String scene, String requestSummary, String responseSummary) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        long started = System.currentTimeMillis();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                insert into ai_call_log
                  (tenant_id, user_id, service_config_id, provider, service_type, model, business_scene, request_summary, response_summary, status, error_message, duration_ms, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', null, ?, now())
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, context.tenantId());
            ps.setLong(2, context.userId());
            ps.setLong(3, config.getId());
            ps.setString(4, config.getProvider());
            ps.setString(5, config.getServiceType());
            ps.setString(6, config.getModel());
            ps.setString(7, scene);
            ps.setString(8, trimSummary(requestSummary));
            ps.setString(9, trimSummary(responseSummary));
            ps.setLong(10, Math.max(1, System.currentTimeMillis() - started));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
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
        if (!List.of("DRAFT", "CONFIRMED", "APPLIED").contains(value)) {
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
