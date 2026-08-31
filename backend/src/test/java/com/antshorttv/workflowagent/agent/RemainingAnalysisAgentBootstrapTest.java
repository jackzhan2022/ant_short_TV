package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "ai.workflow-agent.episode-splitting-enabled=true",
    "ai.workflow-agent.episode-summary-enabled=true",
    "ai.workflow-agent.asset-recognition-enabled=true"
})
class RemainingAnalysisAgentBootstrapTest {
    @Autowired private EpisodeSplittingAgentBootstrap splitting;
    @Autowired private EpisodeSummaryAgentBootstrap summary;
    @Autowired private AssetRecognitionAgentBootstrap recognition;
    @Autowired private WorkflowAgentRepository agents;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsThreeStableDefinitionsAndKeepsStartupIdempotent() throws Exception {
        Long modelId = insertCompatibleModel();
        var arguments = new DefaultApplicationArguments(new String[0]);

        splitting.run(arguments);
        summary.run(arguments);
        recognition.run(arguments);

        assertDefinition(EpisodeSplittingAgentBootstrap.AGENT_CODE, modelId,
            List.of("short-drama-analysis-foundation", "short-drama-episode-splitting-framework"),
            List.of("read_current_script", "read_script_structure", "analyze_script_chunks",
                "save_episode_splitting"), 16384, 10);
        assertDefinition(EpisodeSummaryAgentBootstrap.AGENT_CODE, modelId,
            List.of("short-drama-analysis-foundation", "short-drama-episode-summary-framework"),
            List.of("read_current_episode", "save_episode_summary"), 16384, 4);
        assertDefinition(AssetRecognitionAgentBootstrap.AGENT_CODE, modelId,
            List.of("short-drama-analysis-foundation", "short-drama-asset-recognition-framework"),
            List.of("read_current_episode", "save_episode_assets"), 16384, 4);

        assertThat(agents.get(EpisodeSplittingAgentBootstrap.AGENT_CODE).systemPrompt())
            .contains("不要输出分析过程", "只提交标题和原文边界", "立即调用保存工具");

        splitting.run(arguments);
        summary.run(arguments);
        recognition.run(arguments);

        assertThat(agents.list("short-drama-episode-")).extracting(WorkflowAgentRecord::code)
            .containsExactlyInAnyOrder(
                EpisodeSplittingAgentBootstrap.AGENT_CODE,
                EpisodeSummaryAgentBootstrap.AGENT_CODE);
        assertThat(agents.list(AssetRecognitionAgentBootstrap.AGENT_CODE))
            .extracting(WorkflowAgentRecord::code)
            .containsExactly(AssetRecognitionAgentBootstrap.AGENT_CODE);
    }

    private void assertDefinition(
        String code,
        Long modelId,
        List<String> skills,
        List<String> tools,
        int maxTokens,
        int maxSteps
    ) {
        WorkflowAgentRecord agent = agents.get(code);
        assertThat(agent.status()).isEqualTo("ENABLED");
        assertThat(agent.modelId()).isEqualTo(modelId);
        assertThat(agent.temperature()).isEqualByComparingTo(new BigDecimal("0.200"));
        assertThat(agent.maxTokens()).isEqualTo(maxTokens);
        assertThat(agent.maxSteps()).isEqualTo(maxSteps);
        assertThat(agent.skillCodes()).containsExactlyElementsOf(skills);
        assertThat(agent.toolCodes()).containsExactlyInAnyOrderElementsOf(tools);
        assertThat(agent.systemPrompt()).contains("读取", "分析", "保存", "成功");
    }

    private Long insertCompatibleModel() {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort,
               created_at, updated_at)
            values (?, 'REMAINING_AGENTS_TEST_MODEL', 'Remaining Agents Test Model', 'test-tool-model',
                    'TEXT', 'ENABLED', true, 0, now(), now())
            """, providerId);
        Long modelId = jdbc.queryForObject(
            "select id from ai_model where code = 'REMAINING_AGENTS_TEST_MODEL'", Long.class);
        jdbc.update("""
            insert into ai_model_capability
              (model_id, capability, status, created_at, updated_at)
            values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
            """, modelId);
        return modelId;
    }

}
