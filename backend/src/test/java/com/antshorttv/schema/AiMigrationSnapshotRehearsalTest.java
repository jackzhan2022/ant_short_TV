package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
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
    void preservesAccountSnapshotAndDropsLegacyPointTablesAtCutover() {
        DataSource source = dataSource("ai_migration_source");
        migrate(source, "34");
        JdbcTemplate sourceJdbc = new JdbcTemplate(source);
        seedLegacyHistory(sourceJdbc);

        Path snapshot = tempDir.resolve("pre-v35-snapshot.sql");
        sourceJdbc.execute("SCRIPT TO '" + sqlPath(snapshot) + "'");

        migrate(source, null);
        assertThat(sourceJdbc.queryForObject("select balance from team_point_account where tenant_id = 101", Integer.class)).isEqualTo(90);
        assertThat(sourceJdbc.queryForObject("select count(*) from information_schema.tables where lower(table_name) in ('team_point_transaction', 'ai_point_ledger')", Integer.class)).isZero();
        assertThat(sourceJdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true and version is not null",
                Integer.class))
            .isEqualTo(71);

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

    @Test
    void preservesSettledAccountingHistoryWhenAddingModelPointPricing() {
        DataSource source = dataSource("model_billing_history");
        migrate(source, "50");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedSettledAccountingHistory(jdbc);

        Map<String, Object> costBefore = jdbc.queryForMap(
            "select * from ai_usage_cost_line where id = 9201");
        Map<String, Object> reservationBefore = jdbc.queryForMap(
            "select * from ai_point_reservation where id = 9301");
        Map<String, Object> ledgerBefore = jdbc.queryForMap(
            "select * from point_ledger where id = 9401");

        migrate(source, null);

        Map<String, Object> costAfter = jdbc.queryForMap(
            "select * from ai_usage_cost_line where id = 9201");
        Map<String, Object> reservationAfter = jdbc.queryForMap(
            "select * from ai_point_reservation where id = 9301");
        Map<String, Object> ledgerAfter = jdbc.queryForMap(
            "select * from point_ledger where id = 9401");

        assertThat(costAfter).containsAllEntriesOf(costBefore);
        assertThat(reservationAfter).containsAllEntriesOf(reservationBefore);
        assertThat(ledgerAfter).containsAllEntriesOf(ledgerBefore);
        assertThat(reservationAfter.get("policy_version_id")).isEqualTo(9001L);
        assertThat(reservationAfter.get("point_price_version_id")).isNull();
        assertThat(jdbc.queryForMap("select * from ai_execution_task where id = 9001"))
            .containsEntry("cost_price_version_id", null)
            .containsEntry("point_price_version_id", null);
        assertThat(jdbc.queryForObject(
                "select last_version_no from ai_model_price_version_sequence "
                    + "where model_id = 8801 and price_type = 'COST'",
                Integer.class))
            .isEqualTo(7);
        assertThat(jdbc.queryForObject(
                "select count(*) from ai_model_price_version_sequence "
                    + "where model_id = 8801 and price_type = 'POINT'",
                Integer.class))
            .isZero();
    }

    @Test
    void preservesLegacyAssetsWhenAddingNormalizedAssetWorkflow() {
        DataSource source = dataSource("normalized_asset_rollout");
        migrate(source, "62");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("""
            insert into character_asset
              (id, tenant_id, project_id, name, role_type, status, main_image_result_id,
               main_image_url, created_by, created_at, updated_at)
            values (7101, 7001, 7002, '林夏', 'LEAD', 'CONFIRMED', 7201,
                    '/legacy/character.png', 7003, current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into scene_asset
              (id, tenant_id, project_id, name, scene_type, status, main_image_result_id,
               main_image_url, created_by, created_at, updated_at)
            values (7102, 7001, 7002, '旧公寓', 'INTERIOR', 'PENDING_REVIEW', 7202,
                    '/legacy/scene.png', 7003, current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into prop_asset
              (id, tenant_id, project_id, name, prop_type, status, created_by, created_at, updated_at)
            values (7103, 7001, 7002, '钥匙', 'KEY_PROP', 'DRAFT', 7003,
                    current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into character_asset
              (id, tenant_id, project_id, name, role_type, status, merge_target_id,
               main_image_url, created_by, created_at, updated_at)
            values
              (7104, 7001, 7002, '林夏别名', 'SUPPORTING', 'MERGED', 7101,
               '/legacy/merged.png', 7003, current_timestamp, current_timestamp),
              (7105, 7001, 7002, '已删除角色', 'SUPPORTING', 'DRAFT', null,
               '/legacy/deleted.png', 7003, current_timestamp, current_timestamp)
            """);
        jdbc.update("update character_asset set deleted_at = current_timestamp where id = 7105");

        migrate(source, null);

        assertThat(jdbc.queryForObject(
            "select main_image_url from character_asset where id = 7101", String.class))
            .isEqualTo("/legacy/character.png");
        assertThat(jdbc.queryForObject(
            "select main_image_url from scene_asset where id = 7102", String.class))
            .isEqualTo("/legacy/scene.png");
        assertThat(jdbc.queryForObject(
            "select count(*) from prop_asset where id = 7103 and main_image_result_id is null", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from information_schema.tables where lower(table_name) = 'asset_visual_variant'",
            Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from asset_visual_variant where tenant_id = 7001 and is_primary = true",
            Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
            "select count(*) from asset_visual_variant where asset_type = 'CHARACTER' and asset_id = 7104",
            Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from asset_visual_variant where asset_type = 'CHARACTER' and asset_id = 7105",
            Integer.class)).isZero();
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

    private static void seedSettledAccountingHistory(JdbcTemplate jdbc) {
        jdbc.update("""
            insert into ai_execution_task
              (id, tenant_id, user_id, scene, capability, business_type, business_id,
               requested_model_id, resolved_model_id, status, phase, progress,
               execution_version, client_idempotency_key, trace_id, priority, retryable,
               usage_cost_status, point_settlement_status, reserved_points, settled_points,
               released_points, created_at, updated_at, completed_at)
            values (9001, 8101, 8201, 'legacy_scene', 'TEXT_GENERATION', 'LEGACY_TASK', 8301,
                    8801, 8801, 'SUCCEEDED', 'COMPLETED', 100,
                    1, 'legacy-execution-9001', 'legacy-trace-9001', 100, false,
                    'PRICED', 'SETTLED', 3, 2, 1, timestamp '2026-01-01 00:00:00',
                    timestamp '2026-01-01 00:05:00', timestamp '2026-01-01 00:05:00')
            """);
        jdbc.update("""
            insert into ai_model_price_version
              (id, model_id, version_no, status, effective_from, published_at, created_at)
            values (9101, 8801, 7, 'PUBLISHED', timestamp '2025-12-01 00:00:00',
                    timestamp '2025-11-01 00:00:00', timestamp '2025-11-01 00:00:00')
            """);
        jdbc.update("""
            insert into ai_model_price_component
              (id, price_version_id, metric, unit_size, unit_price, currency,
               dimensions_json, dimensions_key, created_at)
            values (9102, 9101, 'CALL', 1, 0.125, 'USD', '{}', '',
                    timestamp '2025-11-01 00:00:00')
            """);
        jdbc.update("""
            insert into ai_usage_line
              (id, tenant_id, execution_id, model_id, metric, quantity, unit, source,
               dimensions_json, dimensions_key, observed_at, created_at)
            values (9200, 8101, 9001, 8801, 'CALL', 1, 'call', 'PROVIDER_REPORTED',
                    '{}', '', timestamp '2026-01-01 00:04:00', timestamp '2026-01-01 00:04:00')
            """);
        jdbc.update("""
            insert into ai_usage_cost_line
              (id, tenant_id, execution_id, usage_line_id, price_version_id,
               price_component_id, model_id, metric, quantity, unit_size, unit_price,
               currency, raw_cost, rounded_cost, pricing_status, created_at)
            values (9201, 8101, 9001, 9200, 9101, 9102, 8801, 'CALL', 1, 1, 0.125,
                    'USD', 0.125, 0.125, 'PRICED', timestamp '2026-01-01 00:04:00')
            """);
        jdbc.update("""
            insert into ai_point_policy_version
              (id, scene, model_id, capability, version_no, status, effective_from,
               charge_provider_rejection, charge_provider_billed_failure, charge_timeout,
               charge_business_failure, created_at, published_at)
            values (9001, 'legacy_scene', 8801, 'TEXT_GENERATION', 3, 'PUBLISHED',
                    timestamp '2025-12-01 00:00:00', false, true, true, true,
                    timestamp '2025-11-01 00:00:00', timestamp '2025-11-01 00:00:00')
            """);
        jdbc.update("""
            insert into ai_point_reservation
              (id, tenant_id, user_id, execution_id, execution_version, business_type,
               business_id, scene, policy_version_id, status, authorized_usage_json,
               dimensions_json, reserved_points, settled_points, released_points,
               refunded_points, idempotency_key, created_at, settled_at, updated_at)
            values (9301, 8101, 8201, 9001, 1, 'LEGACY_TASK', 8301, 'legacy_scene',
                    9001, 'SETTLED', '{"CALL":1}', '{}', 3, 2, 1, 0,
                    'legacy-reservation-9301', timestamp '2026-01-01 00:00:00',
                    timestamp '2026-01-01 00:05:00', timestamp '2026-01-01 00:05:00')
            """);
        jdbc.update("""
            insert into point_ledger
              (id, tenant_id, user_id, execution_id, execution_version, business_type,
               business_id, reservation_id, policy_version_id, entry_type, amount,
               available_balance_after, reserved_balance_after, idempotency_key,
               description, created_at)
            values (9401, 8101, 8201, 9001, 1, 'LEGACY_TASK', 8301, 9301, 9001,
                    'SETTLE', 2, 98, 0, 'legacy-ledger-9401', 'settled before model pricing',
                    timestamp '2026-01-01 00:05:00')
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
