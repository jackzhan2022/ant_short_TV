package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "ai.workflow-agent.storyboard-enabled=true")
class StoryboardAgentBootstrapTest {
    @Autowired private StoryboardAgentBootstrap bootstrap;
    @Autowired private WorkflowAgentRepository agents;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsEnabledStoryboardAgentWithOrderedSkillsAndTools() throws Exception {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'STORYBOARD_AGENT_TEST_MODEL', 'Storyboard Agent Test', 'storyboard-tool-model',
                    'TEXT', 'ENABLED', true, 0, now(), now())
            """, providerId);
        Long modelId = jdbc.queryForObject(
            "select id from ai_model where code = 'STORYBOARD_AGENT_TEST_MODEL'", Long.class);
        jdbc.update("""
            insert into ai_model_capability (model_id, capability, status, created_at, updated_at)
            values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
            """, modelId);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        WorkflowAgentRecord agent = agents.get(StoryboardAgentBootstrap.AGENT_CODE);
        assertThat(agent.status()).isEqualTo("ENABLED");
        assertThat(agent.modelId()).isEqualTo(modelId);
        assertThat(agent.skillCodes()).containsExactlyElementsOf(StoryboardAgentBootstrap.SKILLS);
        assertThat(agent.toolCodes()).containsExactlyInAnyOrderElementsOf(StoryboardAgentBootstrap.TOOLS);
        assertThat(agent.maxSteps()).isEqualTo(12);
        assertThat(agent.maxTokens()).isEqualTo(16384);
        assertThat(agent.systemPrompt()).contains("save_episode_storyboards", "终止动作");
    }
}
