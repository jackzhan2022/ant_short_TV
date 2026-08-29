package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LegacyAiConfigurationCleanupMigrationTest {

    @Test
    void removesLegacyConfigurationAndPreservesUnrelatedBusinessData() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:legacy_ai_cleanup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""
        );
        migrate(dataSource, "47");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Seed seed = seedLegacyData(jdbc);

        migrate(dataSource, null);

        assertThat(latestVersion(jdbc)).isEqualTo("69");
        assertThat(tableCount(jdbc, "ai_service_config")).isZero();
        assertThat(tableCount(jdbc, "ai_service_test_log")).isZero();
        assertThat(columnCount(jdbc, "ai_image_task", "service_config_id")).isZero();
        assertThat(columnCount(jdbc, "ai_video_task", "service_config_id")).isZero();
        assertThat(columnCount(jdbc, "ai_voice_task", "service_config_id")).isZero();
        assertThat(columnCount(jdbc, "ai_call_log", "service_config_id")).isZero();
        assertThat(columnCount(jdbc, "ai_model", "legacy_service_config_id")).isZero();
        assertThat(columnCount(jdbc, "ai_video_task", "model_id")).isEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from ai_model where id = ?", Integer.class, seed.legacyModelId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_model_capability where model_id = ?", Integer.class, seed.legacyModelId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_model_price_version where model_id = ?", Integer.class, seed.legacyModelId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from project where id = ?", Integer.class, seed.projectId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_model where id = ?", Integer.class, seed.unrelatedModelId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_image_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_video_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_voice_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select model_id from ai_image_task limit 1", Long.class)).isNull();
        assertThat(jdbc.queryForObject("select model_id from ai_video_task limit 1", Long.class)).isNull();

        assertThat(jdbc.queryForObject("select api_key_cipher from ai_provider_config where provider_id = ?", String.class, seed.providerId())).isNull();
        assertThat(jdbc.queryForObject("select status from ai_provider_config where provider_id = ?", String.class, seed.providerId())).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject("select count(*) from permission where code like 'AI_SERVICE:%' and code <> 'AI_SERVICE:USE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from permission where code = 'AI_SERVICE:USE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from permission where code = 'AI_CALL_LOG:VIEW'", Integer.class)).isEqualTo(1);
    }

    private static Seed seedLegacyData(JdbcTemplate jdbc) {
        Long providerId = jdbc.queryForObject("select id from ai_provider where code = 'OpenAI'", Long.class);
        jdbc.update("update ai_provider_config set api_key_cipher = 'legacy-cipher', status = 'ENABLED' where provider_id = ?", providerId);
        jdbc.update("""
            insert into ai_service_config
              (tenant_id, provider, service_type, name, base_url, api_key_cipher, model, priority,
               is_default, enabled, last_test_status, created_by, created_at, updated_at)
            values (9001, 'OpenAI', 'IMAGE', 'Legacy image', 'https://legacy.example', 'cipher',
                    'legacy-image', 100, true, true, 'SUCCESS', 1, now(), now())
            """);
        Long configId = jdbc.queryForObject("select max(id) from ai_service_config", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort,
               legacy_service_config_id, created_at, updated_at)
            values (?, 'LEGACY_CLEANUP_MODEL', 'Legacy cleanup model', 'legacy-image', 'IMAGE',
                    'ENABLED', false, 10, ?, now(), now())
            """, providerId, configId);
        Long legacyModelId = jdbc.queryForObject("select id from ai_model where code = 'LEGACY_CLEANUP_MODEL'", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'UNRELATED_CLEANUP_MODEL', 'Unrelated model', 'unrelated-image', 'IMAGE',
                    'DISABLED', false, 1, now(), now())
            """, providerId);
        Long unrelatedModelId = jdbc.queryForObject("select id from ai_model where code = 'UNRELATED_CLEANUP_MODEL'", Long.class);
        jdbc.update("insert into ai_model_capability (model_id, capability, status, created_at, updated_at) values (?, 'IMAGE_GENERATION', 'ENABLED', now(), now())", legacyModelId);
        jdbc.update("insert into ai_model_price_version (model_id, version_no, status, effective_from, created_at) values (?, 1, 'DRAFT', now(), now())", legacyModelId);
        Long priceVersionId = jdbc.queryForObject("select max(id) from ai_model_price_version", Long.class);
        jdbc.update("insert into ai_model_price_component (price_version_id, metric, unit_size, unit_price, currency, dimensions_key, created_at) values (?, 'IMAGE', 1, 0.1, 'CNY', '', now())", priceVersionId);

        jdbc.update("insert into project (tenant_id, name, code, owner_id, status, created_by, created_at, updated_at) values (9001, 'Preserved project', 'PRESERVED', 1, 'ACTIVE', 1, now(), now())");
        Long projectId = jdbc.queryForObject("select max(id) from project", Long.class);
        jdbc.update("insert into project_ai_config (tenant_id, project_id, image_model_id, created_at, updated_at) values (9001, ?, ?, now(), now())", projectId, legacyModelId);
        jdbc.update("""
            insert into ai_image_task
              (tenant_id, project_id, task_type, target_type, target_id, service_config_id,
               provider_code, model, prompt, aspect_ratio, image_count, status, model_id,
               created_by, created_at, updated_at)
            values (9001, ?, 'STORYBOARD', 'STORYBOARD', 1, ?, 'OpenAI', 'legacy-image',
                    'legacy image task', '9:16', 1, 'SUCCEEDED', ?, 1, now(), now())
            """, projectId, configId, legacyModelId);
        jdbc.update("""
            insert into ai_video_task
              (tenant_id, project_id, storyboard_id, service_config_id, provider_code, model, prompt,
               first_frame_url, duration_seconds, aspect_ratio, status, created_by, created_at, updated_at)
            values (9001, ?, 1, ?, 'OpenAI', 'legacy-video', 'legacy video task',
                    'https://example.com/frame.jpg', 5, '9:16', 'SUCCEEDED', 1, now(), now())
            """, projectId, configId);
        jdbc.update("""
            insert into ai_voice_task
              (tenant_id, project_id, storyboard_id, service_config_id, provider_code, model, voice_type,
               voice_id, text_content, speed, pitch, volume, status, created_by, created_at, updated_at)
            values (9001, ?, 1, ?, 'MiniMax', 'legacy-voice', 'NARRATION', 'voice-1',
                    'local placeholder', 1, 1, 1, 'SUCCEEDED', 1, now(), now())
            """, projectId, configId);
        jdbc.update("""
            insert into ai_call_log
              (tenant_id, user_id, service_config_id, model_id, provider_id, provider, service_type,
               model, business_scene, status, duration_ms, created_at)
            values (9001, 1, ?, ?, ?, 'OpenAI', 'IMAGE', 'legacy-image', 'cleanup-test', 'SUCCESS', 10, now())
            """, configId, legacyModelId, providerId);

        jdbc.update("insert into permission (code, name, type, resource, action, created_at, updated_at) values ('AI_SERVICE:VIEW', 'Legacy view', 'PAGE', 'AI_SERVICE', 'VIEW', now(), now())");
        return new Seed(providerId, legacyModelId, unrelatedModelId, projectId);
    }

    private static void migrate(DataSource dataSource, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        configuration.load().migrate();
    }

    private static String latestVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select version from flyway_schema_history where version is not null order by installed_rank desc limit 1", String.class);
    }

    private static int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("select count(*) from information_schema.tables where lower(table_name) = ?", Integer.class, table);
    }

    private static int columnCount(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("select count(*) from information_schema.columns where lower(table_name) = ? and lower(column_name) = ?", Integer.class, table, column);
    }

    private record Seed(Long providerId, Long legacyModelId, Long unrelatedModelId, Long projectId) {
    }
}
