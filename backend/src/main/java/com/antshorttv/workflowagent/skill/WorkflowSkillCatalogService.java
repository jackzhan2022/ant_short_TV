package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class WorkflowSkillCatalogService {
    private final WorkflowSkillService systemSkills;
    private final TenantWorkflowSkillRepository tenantSkills;

    public WorkflowSkillCatalogService(WorkflowSkillService systemSkills, TenantWorkflowSkillRepository tenantSkills) {
        this.systemSkills = systemSkills;
        this.tenantSkills = tenantSkills;
    }

    public List<WorkflowSkillView> list(String query, String agentCode, String scope, Long tenantId) {
        List<WorkflowSkillView> rows = new ArrayList<>(systemSkills.list(null));
        rows.addAll(tenantSkills.listAll());
        return filter(rows, query, agentCode, scope, tenantId);
    }

    static List<WorkflowSkillView> filter(List<WorkflowSkillView> rows, String query,
        String agentCode, String scope, Long tenantId) {
        if (scope != null && !scope.isBlank() && !List.of("SYSTEM", "TENANT").contains(scope)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Skill 级别不正确。");
        }
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return rows.stream()
            .filter(row -> term.isEmpty() || (row.code() + " " + row.name() + " " + row.description())
                .toLowerCase(Locale.ROOT).contains(term))
            .filter(row -> agentCode == null || agentCode.isBlank() || row.referencingAgentCodes().contains(agentCode))
            .filter(row -> scope == null || scope.isBlank() || scope.equals(row.scope()))
            .filter(row -> tenantId == null || tenantId.equals(row.tenantId()))
            .toList();
    }
}
