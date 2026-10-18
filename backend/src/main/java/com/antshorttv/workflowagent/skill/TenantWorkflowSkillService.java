package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantWorkflowSkillService {
    public static final String NEW_REVISION = "NEW";
    private final TenantContextResolver contexts;
    private final RbacPermissionService permissions;
    private final WorkflowSkillService systemSkills;
    private final TenantWorkflowSkillRepository repository;
    private final SkillDocumentParser parser;
    private final WorkflowAgentProperties properties;

    public TenantWorkflowSkillService(TenantContextResolver contexts, RbacPermissionService permissions,
        WorkflowSkillService systemSkills, TenantWorkflowSkillRepository repository,
        SkillDocumentParser parser, WorkflowAgentProperties properties) {
        this.contexts = contexts;
        this.permissions = permissions;
        this.systemSkills = systemSkills;
        this.repository = repository;
        this.parser = parser;
        this.properties = properties;
    }

    private TenantContext requireManager(Long tenantId) {
        TenantContext context = contexts.requireActiveMember(tenantId);
        if (!"OWNER".equals(context.memberType()) && !permissions.roleCodes(context).contains("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅当前团队所有者或管理员可管理团队 Skill。");
        }
        return context;
    }

    public List<WorkflowSkillView> list(Long tenantId, String query, String agentCode, String scope) {
        contexts.requireActiveMember(tenantId);
        return WorkflowSkillCatalogService.filter(repository.listForTenant(tenantId), query, agentCode, scope, null);
    }

    public WorkflowSkillView detail(Long tenantId, String code) {
        contexts.requireActiveMember(tenantId);
        requireEditable(code);
        return repository.find(tenantId, code).orElseThrow(() ->
            new BusinessException(ErrorCode.WORKFLOW_SKILL_NOT_FOUND, "当前团队尚未创建此 Skill。"));
    }

    @Transactional
    public WorkflowSkillView save(Long tenantId, String code, String content, String expectedRevision) {
        TenantContext actor = requireManager(tenantId);
        requireEditable(code);
        if (content == null || content.getBytes(StandardCharsets.UTF_8).length > properties.getMaxSkillFileBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "SKILL.md 超出允许的文件大小。");
        }
        SkillDocument document = parser.parse(code, content);
        if (document.name().length() > 200 || document.description().length() > 2000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Skill 名称最多 200 字，描述最多 2000 字。");
        }
        WorkflowSkillView current = repository.find(tenantId, code).orElse(null);
        String revision = UUID.randomUUID().toString();
        if (current == null) {
            if (!Objects.equals(NEW_REVISION, expectedRevision)) throw conflict();
            systemSkills.detail(code);
            try {
                repository.insert(tenantId, document, revision, actor.userId());
            } catch (DuplicateKeyException exception) {
                throw conflict();
            }
        } else if (!repository.update(tenantId, document, revision, expectedRevision, actor.userId())) {
            throw conflict();
        }
        return repository.find(tenantId, code).orElseThrow();
    }

    @Transactional
    public void restore(Long tenantId, String code, String expectedRevision) {
        requireManager(tenantId);
        requireEditable(code);
        if (!repository.delete(tenantId, code, expectedRevision)) throw conflict();
    }

    private void requireEditable(String code) {
        if (!TenantStoryboardSkillPolicy.EDITABLE_CODES.contains(code)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "团队仅支持统一的短剧分镜制作 Skill。");
        }
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.WORKFLOW_SKILL_CONFLICT, "Skill 已被修改，请重新加载后再保存或恢复默认。");
    }
}
