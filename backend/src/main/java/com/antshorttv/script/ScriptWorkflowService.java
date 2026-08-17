package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
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
    private final StoryboardMapper storyboardMapper;

    public ScriptWorkflowService(
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        RbacPermissionService rbacPermissionService,
        TenantContextResolver tenantContextResolver,
        ScriptMapper scriptMapper,
        ScriptVersionMapper scriptVersionMapper,
        StoryboardMapper storyboardMapper
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.rbacPermissionService = rbacPermissionService;
        this.tenantContextResolver = tenantContextResolver;
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.storyboardMapper = storyboardMapper;
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
            List.of(),
            List.of(),
            List.of(),
            storyboardMapper.selectByProject(tenantId, projectId)
                .stream()
                .map(StoryboardResponse::from)
                .toList()
        );
    }

    @Transactional
    public ScriptWorkspaceResponse generate(Long tenantId, Long projectId, GenerateScriptRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
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
        version.setStatus("DRAFT");
        version.setCreatedBy(context.userId());
        version.setCreatedAt(now);
        scriptVersionMapper.insert(version);

        script.setCurrentVersionId(version.getId());
        scriptMapper.updateById(script);
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
