package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EpisodeSplittingTokenBudgetMigrationTest {

    @Test
    void restoresOnlyTheBuiltInSplittingBudgetAfterThePublishedIncrease() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:splitting_budget;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""
        );
        migrate(dataSource, "78");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long modelId = insertModel(jdbc);
        insertAgent(jdbc, modelId, "short-drama-episode-splitting", 16384);
        insertAgent(jdbc, modelId, "custom-splitting-agent", 16384);

        migrate(dataSource, null);

        assertThat(jdbc.queryForObject(
            "select max_tokens from ai_workflow_agent where code = 'short-drama-episode-splitting'",
            Integer.class)).isEqualTo(16384);
        assertThat(jdbc.queryForObject(
            "select max_tokens from ai_workflow_agent where code = 'custom-splitting-agent'",
            Integer.class)).isEqualTo(16384);
    }

    private static Long insertModel(JdbcTemplate jdbc) {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort,
               created_at, updated_at)
            values (?, 'SPLITTING_BUDGET_MODEL', 'Splitting budget model', 'tool-model',
                    'TEXT', 'ENABLED', false, 0, now(), now())
            """, providerId);
        return jdbc.queryForObject(
            "select id from ai_model where code = 'SPLITTING_BUDGET_MODEL'", Long.class);
    }

    private static void insertAgent(JdbcTemplate jdbc, Long modelId, String code, int maxTokens) {
        jdbc.update("""
            insert into ai_workflow_agent
              (code, name, system_prompt, model_id, temperature, max_tokens, max_steps,
               status, revision, created_at, updated_at)
            values (?, ?, 'prompt', ?, 0.2, ?, 4, 'ENABLED', 0, now(), now())
            """, code, code, modelId, maxTokens);
    }

    private static void migrate(DataSource dataSource, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        configuration.load().migrate();
    }
}
