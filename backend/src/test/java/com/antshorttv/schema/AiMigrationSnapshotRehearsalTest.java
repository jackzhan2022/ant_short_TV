package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AiMigrationSnapshotRehearsalTest {

    @TempDir
    Path tempDir;

    @Test
    void preservesLegacyHistoryAndRestoresThePreMigrationSnapshot() {
        DataSource source = dataSource("ai_migration_source");
        migrate(source, "34");
        JdbcTemplate sourceJdbc = new JdbcTemplate(source);
        seedLegacyHistory(sourceJdbc);

        Path snapshot = tempDir.resolve("pre-v35-snapshot.sql");
        sourceJdbc.execute("SCRIPT TO '" + sqlPath(snapshot) + "'");

        migrate(source, null);
        assertLegacyHistory(sourceJdbc);
        assertThat(sourceJdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true and version is not null",
                Integer.class))
            .isEqualTo(47);

        DataSource restored = dataSource("ai_migration_restored");
        JdbcTemplate restoredJdbc = new JdbcTemplate(restored);
        restoredJdbc.execute("RUNSCRIPT FROM '" + sqlPath(snapshot) + "'");
        assertLegacyHistory(restoredJdbc);
        assertThat(restoredJdbc.queryForObject(
                "select version from flyway_schema_history where version is not null "
                    + "order by installed_rank desc limit 1",
                String.class))
            .isEqualTo("34");
        assertThat(restoredJdbc.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'ai_execution_task'",
                Integer.class))
            .isZero();
    }

    private static DataSource dataSource(String name) {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void migrate(DataSource dataSource, String target) {
        var configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        configuration.load().migrate();
    }

    private static void seedLegacyHistory(JdbcTemplate jdbc) {
        jdbc.update("""
            insert into ai_call_log
              (tenant_id, user_id, service_config_id, provider, service_type, model,
               business_scene, request_summary, response_summary, status, error_message,
               duration_ms, created_at)
            values (101, 201, null, 'OpenAI', 'TEXT', 'legacy-model',
                    'script_generate', 'legacy request', 'legacy response', 'SUCCESS', null,
                    321, current_timestamp)
            """);
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (101, 90, 100, 10, current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into team_point_transaction
              (tenant_id, user_id, transaction_type, change_amount, balance_after,
               business_scene, business_id, description, created_at)
            values (101, 201, 'AI_CONSUME', -1, 90,
                    'script_generate', 301, 'legacy point history', current_timestamp)
            """);
    }

    private static void assertLegacyHistory(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "select request_summary from ai_call_log where tenant_id = 101", String.class))
            .isEqualTo("legacy request");
        assertThat(jdbc.queryForObject(
                "select balance from team_point_account where tenant_id = 101", Integer.class))
            .isEqualTo(90);
        assertThat(jdbc.queryForObject(
                "select description from team_point_transaction where tenant_id = 101", String.class))
            .isEqualTo("legacy point history");
    }

    private static String sqlPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
    }
}
