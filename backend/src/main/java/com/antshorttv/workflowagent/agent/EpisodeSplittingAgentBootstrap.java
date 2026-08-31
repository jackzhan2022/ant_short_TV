package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EpisodeSplittingAgentBootstrap extends AbstractAnalysisAgentBootstrap {
    public static final String AGENT_CODE = "short-drama-episode-splitting";

    public EpisodeSplittingAgentBootstrap(
        WorkflowAgentRepository repository,
        WorkflowAgentService service,
        JdbcTemplate jdbc,
        @Value("${ai.workflow-agent.episode-splitting-enabled:false}") boolean enabled
    ) {
        super(repository, service, jdbc, enabled);
    }

    @Override protected String agentCode() {
        return AGENT_CODE;
    }

    @Override protected WorkflowAgentCommand definition(Long modelId) {
        return new WorkflowAgentCommand(
            AGENT_CODE, "剧集智能拆分", "独立读取当前剧本并生成覆盖全文的正式剧集。",
            "先按当前运行模式调用指定读取工具。读取后静默判断，不要输出分析过程、逐集论证或复述原文；只提交标题和原文边界，并立即调用保存工具。分块模式只使用工具返回的候选与可信锚点。保存成功前不得声称完成。",
            modelId, new BigDecimal("0.200"), 16384, 10, "ENABLED",
            List.of("short-drama-analysis-foundation", "short-drama-episode-splitting-framework"),
            List.of("read_current_script", "read_script_structure", "analyze_script_chunks", "save_episode_splitting"));
    }
}
