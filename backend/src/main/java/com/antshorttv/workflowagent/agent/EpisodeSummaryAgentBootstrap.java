package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EpisodeSummaryAgentBootstrap extends AbstractAnalysisAgentBootstrap {
    public static final String AGENT_CODE = "short-drama-episode-summary";

    public EpisodeSummaryAgentBootstrap(
        WorkflowAgentRepository repository,
        WorkflowAgentService service,
        JdbcTemplate jdbc,
        @Value("${ai.workflow-agent.episode-summary-enabled:false}") boolean enabled
    ) {
        super(repository, service, jdbc, enabled);
    }

    @Override protected String agentCode() {
        return AGENT_CODE;
    }

    @Override protected WorkflowAgentCommand definition(Long modelId) {
        return new WorkflowAgentCommand(
            AGENT_CODE, "剧集概要提炼", "独立读取一集当前正文并生成可编辑正式概要。",
            "严格按已加载 Skill 执行：先读取当前剧集，再分析本集事实，最后调用保存工具；保存成功前不得声称完成。",
            modelId, new BigDecimal("0.200"), 16384, 4, "ENABLED",
            List.of("short-drama-analysis-foundation", "short-drama-episode-summary-framework"),
            List.of("read_current_episode", "save_episode_summary"));
    }
}
