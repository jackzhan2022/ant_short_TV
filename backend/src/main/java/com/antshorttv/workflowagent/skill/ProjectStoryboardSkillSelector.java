package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.StoryboardSkillVersion;
import com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectStoryboardSkillSelector {
    private final ProjectMapper projects;

    public ProjectStoryboardSkillSelector(ProjectMapper projects) { this.projects = projects; }

    public List<String> select(String agentCode, Long tenantId, Long projectId, List<String> configured) {
        if (!StoryboardAgentBootstrap.AGENT_CODE.equals(agentCode) || projectId == null) return configured;
        if (tenantId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "项目分镜必须指定所属团队。");
        ProjectEntity project = projects.selectByTenantIdAndId(tenantId, projectId);
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        StoryboardSkillVersion version = StoryboardSkillVersion.resolve(project.storyboardSkillVersion);
        if (version == StoryboardSkillVersion.V1) return configured;
        java.util.Set<String> productionSkills = java.util.Set.of(
            StoryboardSkillVersion.V1.skillCode(), StoryboardSkillVersion.V2.skillCode(),
            "short-drama-storyboard-planning", "short-drama-storyboard-material-reference",
            "short-drama-seedance-video-prompt");
        java.util.ArrayList<String> selected = new java.util.ArrayList<>();
        selected.add(version.skillCode());
        configured.stream().filter(code -> !productionSkills.contains(code)).forEach(selected::add);
        return List.copyOf(selected);
    }
}
