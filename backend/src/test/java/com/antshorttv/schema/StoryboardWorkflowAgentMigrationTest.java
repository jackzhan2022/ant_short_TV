package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StoryboardWorkflowAgentMigrationTest {
    @Test
    void keepsLegacyRowsAndAddsEpisodeScopedFormalColumnsAndIndexes() {
        DataSource source = new DriverManagerDataSource(
            "jdbc:h2:mem:storyboard_agent_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", "");
        migrate(source, "86");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, episode_no, shot_no, visual_description, status,
               created_by, created_at, updated_at)
            values (1, 2, 3, 4, 'legacy', 'DRAFT', 5, now(), now())
            """);

        migrate(source, null);

        assertThat(jdbc.queryForObject(
            "select storyboard_no from storyboard where visual_description = 'legacy'", Integer.class))
            .isEqualTo(4);
        assertThat(jdbc.queryForMap("""
            select episode_id, shot_plan_json, prompt_document_json, source_fingerprint,
                   generated_by_run_id, material_binding_status
              from storyboard where visual_description = 'legacy'
            """).get("material_binding_status")).isEqualTo("LEGACY");
        assertThat(jdbc.queryForList("""
            select index_name from information_schema.indexes
             where table_name = 'storyboard'
            """, String.class)).contains("idx_storyboard_active_episode_order", "idx_storyboard_generated_run");
    }

    private void migrate(DataSource source, String target) {
        var configuration = Flyway.configure().dataSource(source)
            .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        configuration.load().migrate();
    }
}
