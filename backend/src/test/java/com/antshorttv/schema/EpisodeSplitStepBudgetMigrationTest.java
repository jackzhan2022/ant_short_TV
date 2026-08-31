package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EpisodeSplitStepBudgetMigrationTest {
    @Test
    void upgradesExistingEpisodeSplittingAgentForCorrectiveFallbackSteps() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:episode_split_step_budget;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", "");
        migrate(dataSource, "80");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long modelId = jdbc.queryForObject("select min(id) from ai_model", Long.class);
        jdbc.update("""
            insert into ai_workflow_agent
              (code, name, description, system_prompt, model_id, temperature, max_tokens,
               max_steps, status, revision, created_at, updated_at)
            values ('short-drama-episode-splitting', 'Split', '', 'split', ?, 0.2, 16384,
                    4, 'ENABLED', 0, now(), now())
            """, modelId);

        migrate(dataSource, null);

        assertThat(jdbc.queryForObject("""
            select max_steps from ai_workflow_agent
             where code = 'short-drama-episode-splitting'
            """, Integer.class)).isEqualTo(16);
        assertThat(jdbc.queryForList("""
            select tool.tool_code
              from ai_workflow_agent_tool tool
              join ai_workflow_agent agent on agent.id = tool.agent_id
             where agent.code = 'short-drama-episode-splitting'
               and tool.tool_code in ('read_script_structure', 'analyze_script_chunks')
             order by tool.tool_code
            """, String.class)).containsExactly("analyze_script_chunks", "read_script_structure");
    }

    private void migrate(DataSource dataSource, String target) {
        var configuration = Flyway.configure().dataSource(dataSource)
            .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        configuration.load().migrate();
    }
}
