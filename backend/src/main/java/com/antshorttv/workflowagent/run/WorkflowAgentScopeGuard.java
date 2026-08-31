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
        "read_project_context", "list_episode_scripts", "read_episode_script", "read_project_full_script",
        "read_adjacent_episodes", "read_script_analysis", "read_script_assets",
        "save_episode_script", "read_current_script", "save_global_understanding",
        "read_current_episode", "save_episode_splitting", "save_episode_summary",
        "save_episode_assets", "read_script_structure", "analyze_script_chunks"
    );
    private static final Set<String> EPISODE_TOOLS = Set.of(
        "read_episode_script", "read_adjacent_episodes", "save_episode_script",
        "read_current_episode", "save_episode_summary", "save_episode_assets"
    );
    private static final Set<String> SCRIPT_TOOLS = Set.of(
        "read_current_script", "save_global_understanding", "read_current_episode",
        "save_episode_splitting", "save_episode_summary", "save_episode_assets"
        , "read_script_structure", "analyze_script_chunks"
    );
    private static final Set<String> WRITE_TOOLS = Set.of(
        "save_episode_script", "save_global_understanding", "save_episode_splitting",
        "save_episode_summary", "save_episode_assets"
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
        boolean needsScript = toolCodes.stream().anyMatch(SCRIPT_TOOLS::contains);
        if (needsProject && input.projectId() == null) {
            throw invalid("所选工具需要项目作用域。");
        }
        if (needsEpisode && input.episodeId() == null) {
            throw invalid("所选工具需要剧集作用域。");
        }
        if (needsScript && input.scriptId() == null) {
            throw invalid("所选工具需要剧本作用域。");
        }
        if (input.projectId() != null) {
            if (input.executionId() != null) {
                requireTrustedExecutionScope(input);
            } else {
                String permission = toolCodes.stream().anyMatch(WRITE_TOOLS::contains)
                    ? "SCRIPT:EDIT" : "SCRIPT:VIEW";
                permissions.require(input.tenantId(), input.projectId(), permission);
            }
        }
        if (input.episodeId() != null) {
            Integer count = jdbc.queryForObject("""
                select count(*) from script_episode
                 where id = ? and tenant_id = ? and project_id = ? and script_id = ?
                   and status = 'ACTIVE' and retired_at is null
                """, Integer.class, input.episodeId(), input.tenantId(), input.projectId(), input.scriptId());
            if (count == null || count != 1) {
                throw invalid("剧集不属于当前授权项目。");
            }
        }
        if (input.scriptId() != null) {
            Integer count = jdbc.queryForObject("""
                select count(*) from script
                 where id = ? and tenant_id = ? and project_id = ? and deleted_at is null
                """, Integer.class, input.scriptId(), input.tenantId(), input.projectId());
            if (count == null || count != 1) {
                throw invalid("剧本不属于当前授权项目或已删除。");
            }
        }
        if (input.analysisStageId() != null) {
            String stageCode = expectedStageCode(input.agentCode());
            if (stageCode == null) {
                throw invalid("Agent 不允许绑定剧本分析阶段。");
            }
            Integer count = jdbc.queryForObject("""
                select count(*)
                  from script_analysis_stage stage
                  join script_analysis_task task on task.id = stage.task_id
                 where stage.id = ? and stage.task_id = ? and stage.stage_code = ?
                   and task.tenant_id = ? and task.project_id = ? and task.script_id = ?
                """, Integer.class, input.analysisStageId(), input.taskId(), stageCode, input.tenantId(),
                input.projectId(), input.scriptId());
            if (count == null || count != 1) {
                throw invalid("分析阶段不属于当前可信剧本任务。");
            }
        }
    }

    static String expectedStageCode(String agentCode) {
        return switch (agentCode == null ? "" : agentCode) {
            case "short-drama-global-understanding" -> "GLOBAL_UNDERSTANDING";
            case "short-drama-episode-splitting" -> "EPISODE_SPLITTING";
            case "short-drama-episode-summary" -> "EPISODE_SUMMARY";
            case "short-drama-asset-recognition" -> "CHARACTER_SCENE_RECOGNITION";
            default -> null;
        };
    }

    private void requireTrustedExecutionScope(WorkflowAgentRunInput input) {
        if (input.attemptId() == null || input.executionVersion() == null || input.taskId() == null) {
            throw invalid("后台 Agent 缺少可信执行身份。");
        }
        Integer count = jdbc.queryForObject("""
            select count(*)
              from ai_execution_task execution
              join ai_execution_attempt attempt
                on attempt.id = ? and attempt.execution_id = execution.id
               and attempt.execution_version = execution.execution_version
              join script_analysis_task task
                on task.id = execution.business_id and task.execution_id = execution.id
             where execution.id = ? and execution.execution_version = ?
               and execution.tenant_id = ? and execution.project_id = ? and execution.user_id = ?
               and execution.business_type = 'SCRIPT_ANALYSIS_TASK'
               and task.id = ? and task.tenant_id = ? and task.project_id = ? and task.script_id = ?
            """, Integer.class, input.attemptId(), input.executionId(), input.executionVersion(),
            input.tenantId(), input.projectId(), input.userId(), input.taskId(), input.tenantId(),
            input.projectId(), input.scriptId());
        if (count == null || count != 1) {
            throw invalid("后台 Agent 执行身份与剧本任务不匹配。");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
