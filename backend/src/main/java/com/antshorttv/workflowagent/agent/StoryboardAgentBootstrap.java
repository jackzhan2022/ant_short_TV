package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.util.List;

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
        JdbcTemplate jdbc
    ) {
        super(repository, service, jdbc);
    }

    @Override protected String agentCode() { return AGENT_CODE; }

    @Override protected WorkflowAgentCommand definition(Long modelId) {
        return new WorkflowAgentCommand(
            AGENT_CODE, "分镜规划", "按当前有效剧集规划并正式保存完整多镜头视频分镜。",
            "服务端已准备完整可信上下文。严格按 Skill 用 schemaVersion 3 完成整集分镜；为每个分镜提交创意终点 sourceTo，内部镜头可提交 sourceAnchor，并用 assetKey、可选 variantKey、role 和 sourceName 提交实际使用的结构化素材引用。编号、锚点、时长和声音归属由后端规范化，可信原文由后端派生，不要枚举 soundSegmentIds。将表演、情绪和运镜分别写入对应字段。只调用 save_episode_storyboards，并以保存成功作为终止动作。",
            modelId, new BigDecimal("0.300"), 16384, 14, "ENABLED", SKILLS, TOOLS);
    }
}
