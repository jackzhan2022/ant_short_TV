package com.antshorttv.workflowagent.agent;

import com.antshorttv.review.ReviewDimension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ScriptReviewAgentBootstrap extends AbstractAnalysisAgentBootstrap {
    public static final String AGENT_CODE = "script-review";

    public ScriptReviewAgentBootstrap(WorkflowAgentRepository repository, WorkflowAgentService service,
        JdbcTemplate jdbc,
        @Value("${ai.workflow-agent.review-bootstrap-enabled:true}") boolean enabled) {
        super(repository, service, jdbc, enabled);
    }

    @Override
    protected String agentCode() {
        return AGENT_CODE;
    }

    @Override
    protected WorkflowAgentCommand definition(Long modelId) {
        List<String> skills = new ArrayList<>();
        skills.add("script-review-foundation");
        skills.add("script-review-execution-framework");
        Arrays.stream(ReviewDimension.values()).map(ReviewDimension::skillCode).forEach(skills::add);
        skills.add("script-review-cross-episode-synthesis");
        return new WorkflowAgentCommand(AGENT_CODE, "剧本审核", "按所选维度独立审核当前剧本版本，支持快速审核与深度分段聚合。",
            "只使用可信审核工具读取当前冻结范围。严格按当前阶段加载的 Skill 与工具顺序执行；"
                + "不得臆造证据，不得越过所选维度或范围，且保存工具成功前不得声称完成。",
            modelId, new BigDecimal("0.100"), 16384, 20, "ENABLED", List.copyOf(skills),
            List.of("read_review_context", "read_review_content", "read_review_issue_history",
                "save_review_unit_result", "read_review_unit_results", "save_review_result"));
    }
}
