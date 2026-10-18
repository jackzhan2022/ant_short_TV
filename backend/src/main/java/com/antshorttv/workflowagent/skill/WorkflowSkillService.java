package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class WorkflowSkillService {
    private final FileSkillRepository repository;
    private final SkillReferenceLookup references;
    private final TenantWorkflowSkillRepository tenantSkills;

    public WorkflowSkillService(FileSkillRepository repository, SkillReferenceLookup references) {
        this(repository, references, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowSkillService(FileSkillRepository repository, SkillReferenceLookup references,
        TenantWorkflowSkillRepository tenantSkills) {
        this.repository = repository;
        this.references = references;
        this.tenantSkills = tenantSkills;
    }

    public WorkflowSkillView findTenantOverride(String agentCode, Long tenantId, String code) {
        if (tenantSkills == null || tenantId == null || !TenantStoryboardSkillPolicy.canOverride(agentCode, code)) {
            return null;
        }
        return tenantSkills.find(tenantId, code).orElse(null);
    }

    public List<WorkflowSkillView> list(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return repository.list().stream()
            .filter(skill -> normalized.isEmpty()
                || skill.code().toLowerCase(Locale.ROOT).contains(normalized)
                || skill.name().toLowerCase(Locale.ROOT).contains(normalized)
                || skill.description().toLowerCase(Locale.ROOT).contains(normalized))
            .map(this::view)
            .toList();
    }

    public WorkflowSkillView detail(String code) {
        return view(repository.get(code));
    }

    public WorkflowSkillView create(String code, String content) {
        return view(repository.create(code, content));
    }

    public WorkflowSkillView update(String code, String content, String expectedRevision) {
        return view(repository.update(code, content, expectedRevision));
    }

    public WorkflowSkillView copy(String sourceCode, String targetCode) {
        return view(repository.copy(sourceCode, targetCode));
    }

    public void delete(String code) {
        if (tenantSkills != null && tenantSkills.hasOverrides(code)) {
            throw new BusinessException(ErrorCode.WORKFLOW_SKILL_IN_USE, "Skill 存在团队定制版本，不能删除系统来源。");
        }
        List<String> agentCodes = references.findAgentCodes(code);
        if (!agentCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.WORKFLOW_SKILL_IN_USE,
                "Skill 正被 Agent 引用：" + String.join(", ", agentCodes));
        }
        repository.delete(code);
    }

    private WorkflowSkillView view(SkillDocument document) {
        return new WorkflowSkillView(document.code(), document.name(), document.description(),
            document.content(), document.revision(), List.copyOf(references.findAgentCodes(document.code())));
    }
}
