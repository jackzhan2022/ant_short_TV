package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.review.ReviewAgentExecutionPlanFactory;
import com.antshorttv.review.ReviewDimension;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "ai.workflow-agent.review-bootstrap-enabled=true")
class ScriptReviewAgentBootstrapTest {
    @Autowired private ScriptReviewAgentBootstrap bootstrap;
    @Autowired private WorkflowAgentRepository agents;
    @Autowired private ReviewAgentExecutionPlanFactory plans;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void bootstrapsMaximumDefinitionAndNarrowsEachFrozenPhase() {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'REVIEW_AGENT_TEST_MODEL', 'Review Agent Test Model', 'review-tool-model',
                    'TEXT', 'ENABLED', false, 2, now(), now())
            """, providerId);
        Long modelId = jdbc.queryForObject(
            "select id from ai_model where code = 'REVIEW_AGENT_TEST_MODEL'", Long.class);
        jdbc.update("insert into ai_model_capability (model_id, capability, status, created_at, updated_at) values (?, 'TOOL_CALLING', 'ENABLED', now(), now())", modelId);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        WorkflowAgentRecord maximum = agents.get(ScriptReviewAgentBootstrap.AGENT_CODE);
        assertThat(maximum.status()).isEqualTo("ENABLED");
        assertThat(maximum.maxSteps()).isEqualTo(20);
        assertThat(maximum.skillCodes()).hasSize(16).contains(
            "script-review-foundation", "script-review-execution-framework",
            "script-review-cross-episode-synthesis");
        assertThat(maximum.toolCodes()).containsExactlyInAnyOrder(
            "read_review_context", "read_review_content", "read_review_issue_history",
            "save_review_unit_result", "read_review_unit_results", "save_review_result");

        WorkflowAgentExecutionPlan quick = plans.freeze(List.of("台词合理性"), "QUICK");
        assertThat(quick.skillSnapshots()).extracting(skill -> skill.code()).containsExactly(
            "script-review-foundation", "script-review-execution-framework",
            ReviewDimension.DIALOGUE.skillCode());
        assertThat(quick.agent().toolCodes()).containsExactly(
            "read_review_context", "read_review_content", "read_review_issue_history", "save_review_result");

        WorkflowAgentExecutionPlan child = plans.freeze(List.of("台词合理性"), "DEEP_CHILD");
        assertThat(child.agent().toolCodes()).containsExactly(
            "read_review_context", "read_review_content", "read_review_issue_history", "save_review_unit_result");
        WorkflowAgentExecutionPlan aggregation = plans.freeze(List.of("台词合理性"), "DEEP_AGGREGATION");
        assertThat(aggregation.skillSnapshots()).extracting(skill -> skill.code())
            .endsWith("script-review-cross-episode-synthesis");
        assertThat(aggregation.agent().toolCodes()).containsExactly(
            "read_review_context", "read_review_issue_history", "read_review_unit_results", "save_review_result");
    }
}
