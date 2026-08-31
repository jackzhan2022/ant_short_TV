package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class GlobalUnderstandingAgentBootstrapTest {
    @Autowired
    private GlobalUnderstandingAgentBootstrap bootstrap;
    @Autowired
    private WorkflowAgentRepository agents;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsTheEnabledAgentOnceAndPreservesAdministratorChanges() throws Exception {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort,
               created_at, updated_at)
            values (?, 'GLOBAL_AGENT_TEST_MODEL', 'Global Agent Test Model', 'test-tool-model',
                    'TEXT', 'ENABLED', false, 1, now(), now())
            """, providerId);
        Long modelId = jdbc.queryForObject(
            "select id from ai_model where code = 'GLOBAL_AGENT_TEST_MODEL'", Long.class);
        jdbc.update("""
            insert into ai_model_capability
              (model_id, capability, status, created_at, updated_at)
            values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
            """, modelId);
        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        WorkflowAgentRecord created = agents.get(GlobalUnderstandingAgentBootstrap.AGENT_CODE);

        assertThat(created.status()).isEqualTo("ENABLED");
        assertThat(created.maxSteps()).isEqualTo(4);
        assertThat(created.maxTokens()).isEqualTo(16384);
        assertThat(created.skillCodes()).containsExactly(
            "short-drama-analysis-foundation",
            "short-drama-global-understanding-framework");
        assertThat(created.toolCodes()).containsExactlyInAnyOrder(
            "read_current_script", "save_global_understanding");
        assertThat(created.systemPrompt()).contains("读取", "分析", "保存");

        WorkflowAgentCommand changed = new WorkflowAgentCommand(
            created.code(), "管理员自定义名称", created.description(), created.systemPrompt(),
            created.modelId(), created.temperature(), created.maxTokens(), created.maxSteps(),
            created.status(), created.skillCodes(), created.toolCodes());
        agents.update(created.code(), created.revision(), changed, null);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        WorkflowAgentRecord afterRestart = agents.get(created.code());
        assertThat(afterRestart.name()).isEqualTo("管理员自定义名称");
        assertThat(afterRestart.revision()).isEqualTo(created.revision() + 1);
        assertThat(agents.list(created.code())).extracting(WorkflowAgentRecord::code)
            .isEqualTo(List.of(created.code()));
    }
}
