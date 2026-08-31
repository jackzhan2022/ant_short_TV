package com.antshorttv.workflowagent.agent;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AssetRecognitionAgentBootstrap extends AbstractAnalysisAgentBootstrap {
    public static final String AGENT_CODE = "short-drama-asset-recognition";

    public AssetRecognitionAgentBootstrap(
        WorkflowAgentRepository repository,
        WorkflowAgentService service,
        JdbcTemplate jdbc,
        @Value("${ai.workflow-agent.asset-recognition-enabled:false}") boolean enabled
    ) {
        super(repository, service, jdbc, enabled);
    }

    @Override protected String agentCode() {
        return AGENT_CODE;
    }

    @Override protected WorkflowAgentCommand definition(Long modelId) {
        return new WorkflowAgentCommand(
            AGENT_CODE, "角色场景道具识别", "独立读取一集当前正文并保存正式角色、变装、场景、道具及形态。",
            "严格按已加载 Skill 执行：先读取当前剧集，再分析实体与证据，最后调用保存工具；保存成功前不得声称完成。",
            modelId, new BigDecimal("0.200"), 16384, 4, "ENABLED",
            List.of("short-drama-analysis-foundation", "short-drama-asset-recognition-framework"),
            List.of("read_current_episode", "save_episode_assets"));
    }
}
