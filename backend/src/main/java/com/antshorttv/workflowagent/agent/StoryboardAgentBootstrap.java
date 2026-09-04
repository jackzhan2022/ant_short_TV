package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StoryboardAgentBootstrap extends AbstractAnalysisAgentBootstrap {
    public static final String AGENT_CODE = "short-drama-storyboard";
    public static final List<String> SKILLS = List.of(
        "short-drama-analysis-foundation",
        "short-drama-storyboard-planning",
        "short-drama-storyboard-material-reference",
        "short-drama-seedance-video-prompt");
    public static final List<String> TOOLS = List.of(
        "read_current_episode",
        "read_adjacent_episodes",
        "read_script_analysis",
        "read_project_context",
        "read_script_assets",
        "save_episode_storyboards");

    public StoryboardAgentBootstrap(
        WorkflowAgentRepository repository,
        WorkflowAgentService service,
        JdbcTemplate jdbc,
        @Value("${ai.workflow-agent.storyboard-enabled:false}") boolean enabled
    ) {
        super(repository, service, jdbc, enabled);
    }

    @Override protected String agentCode() { return AGENT_CODE; }

    @Override protected WorkflowAgentCommand definition(Long modelId) {
        return new WorkflowAgentCommand(
            AGENT_CODE, "分镜规划", "按当前有效剧集规划并正式保存完整多镜头视频分镜。",
            "严格按 Skill 和工具顺序完成整集分镜。只提交结构化事实，必须以 save_episode_storyboards 成功作为终止动作。",
            modelId, new BigDecimal("0.300"), 16384, 12, "ENABLED", SKILLS, TOOLS);
    }
}
