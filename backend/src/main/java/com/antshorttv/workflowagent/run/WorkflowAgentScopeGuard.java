package com.antshorttv.workflowagent.run;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAgentScopeGuard {
    private static final Set<String> PROJECT_TOOLS = Set.of(
        "read_project_context", "list_episode_scripts", "read_episode_script",
        "read_adjacent_episodes", "read_script_analysis", "read_script_assets",
        "save_episode_script"
    );
    private static final Set<String> EPISODE_TOOLS = Set.of(
        "read_episode_script", "read_adjacent_episodes", "save_episode_script"
    );

    private final ProjectPermissionGuard permissions;
    private final JdbcTemplate jdbc;

    public WorkflowAgentScopeGuard(ProjectPermissionGuard permissions, JdbcTemplate jdbc) {
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    public void requireAuthorized(WorkflowAgentRunInput input, List<String> toolCodes) {
        boolean needsProject = toolCodes.stream().anyMatch(PROJECT_TOOLS::contains);
        boolean needsEpisode = toolCodes.stream().anyMatch(EPISODE_TOOLS::contains);
        if (needsProject && input.projectId() == null) {
            throw invalid("所选工具需要项目作用域。");
        }
        if (needsEpisode && input.episodeId() == null) {
            throw invalid("所选工具需要剧集作用域。");
        }
        if (input.projectId() != null) {
            String permission = toolCodes.contains("save_episode_script") ? "SCRIPT:EDIT" : "SCRIPT:VIEW";
            permissions.require(input.tenantId(), input.projectId(), permission);
        }
        if (input.episodeId() != null) {
            Integer count = jdbc.queryForObject("""
                select count(*) from script_episode
                 where id = ? and tenant_id = ? and project_id = ? and retired_at is null
                """, Integer.class, input.episodeId(), input.tenantId(), input.projectId());
            if (count == null || count != 1) {
                throw invalid("剧集不属于当前授权项目。");
            }
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
