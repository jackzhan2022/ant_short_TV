package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetExtractionSkillService {
    public static final String AGENT = "short-drama-asset-recognition";
    public static final String DEFAULT = "short-drama-asset-recognition-framework";
    public static final String LIVE_ACTION = "short-drama-asset-recognition-live-action";
    private final FileSkillRepository skills;
    private final ProjectMapper projects;

    public AssetExtractionSkillService(FileSkillRepository skills, ProjectMapper projects) {
        this.skills = skills;
        this.projects = projects;
    }

    public record Option(String code, String name, String description, String revision, String promptContract) {}

    public List<Option> list() {
        return skills.list().stream().filter(AssetExtractionSkillService::selectable)
            .map(s -> new Option(s.code(), s.name(), s.description(), s.revision(), s.promptContract())).toList();
    }

    public static boolean selectable(SkillDocument skill) {
        return DEFAULT.equals(skill.code()) || "asset-extraction".equals(skill.purpose());
    }

    public String validate(String code) {
        String resolved = code == null || code.isBlank() ? DEFAULT : code;
        if (!selectable(skills.get(resolved)))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择有效的资产提取方案。");
        return resolved;
    }

    public String projectCode(Long tenantId, Long projectId) {
        if (projectId == null) return DEFAULT;
        if (tenantId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "资产提取必须指定所属团队。");
        var project = projects.selectByTenantIdAndId(tenantId, projectId);
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        return validate(project.assetExtractionSkillCode);
    }

    public List<String> select(String agent, Long tenant, Long project, List<String> configured) {
        if (!AGENT.equals(agent)) return configured;
        String selected = projectCode(tenant, project);
        return selectCode(selected, configured);
    }

    public List<String> selectCode(String selectedCode, List<String> configured) {
        String selected = validate(selectedCode);
        List<String> result = new ArrayList<>();
        result.add("short-drama-analysis-foundation");
        result.add(selected);
        for (String code : configured) {
            if (!result.contains(code) && !selectable(skills.get(code))) result.add(code);
        }
        return List.copyOf(result);
    }

    public String fingerprint(Long tenant, Long project) {
        String code = projectCode(tenant, project);
        return code + ":" + skills.get(code).revision() + ":" + skills.get("short-drama-analysis-foundation").revision();
    }
}
