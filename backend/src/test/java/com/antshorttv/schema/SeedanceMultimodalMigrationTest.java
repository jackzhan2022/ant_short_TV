package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SeedanceMultimodalMigrationTest {

    @Test
    void createsSeedanceMultimodalSchemaOnFreshInstall() {
        DataSource dataSource = dataSource("seedance_multimodal_fresh");
        migrate(dataSource, null);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(latestVersion(jdbc)).isEqualTo("119");
        assertThat(jdbc.queryForObject("""
            select count(*) from ai_model
             where code in ('SEEDANCE_2_0_MINI', 'SEEDANCE_2_0_FAST',
                            'SEEDANCE_2_0_STANDARD', 'SEEDANCE_2_5')
               and service_type = 'VIDEO'
               and config_json is not null
            """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
            select count(*) from ai_model
             where code = 'SEEDANCE_2_0_MINI'
               and is_default = true
            """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            select count(*)
              from ai_model_capability capability
              join ai_model model on model.id = capability.model_id
             where model.code in ('SEEDANCE_2_0_MINI', 'SEEDANCE_2_0_FAST',
                                  'SEEDANCE_2_0_STANDARD', 'SEEDANCE_2_5')
               and capability.capability = 'VIDEO_GENERATION'
            """, Integer.class)).isEqualTo(4);

        assertThat(columnCount(jdbc, "project", "video_resolution")).isEqualTo(1);
        assertThat(columnCount(jdbc, "project", "video_generate_audio")).isEqualTo(1);
        assertThat(columnCount(jdbc, "project", "video_watermark")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task", "compiled_prompt")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task", "generate_audio")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task", "watermark")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task", "request_snapshot_json")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task", "provider_result_metadata_json")).isEqualTo(1);
        assertThat(tableCount(jdbc, "ai_video_task_reference")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task_reference", "media_type")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task_reference", "media_index")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task_reference", "provider_url")).isEqualTo(1);
        assertThat(columnCount(jdbc, "ai_video_task_reference", "duration_seconds")).isEqualTo(1);
    }

    @Test
    void upgradesFromV118WithoutReplacingModelsAndClearsOnlyVersionOnePromptDocuments() {
        DataSource dataSource = dataSource("seedance_multimodal_upgrade");
        migrate(dataSource, "118");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Long> modelIds = Map.of(
            "SEEDANCE_2_0_FAST", modelId(jdbc, "SEEDANCE_2_0_FAST"),
            "SEEDANCE_2_0_STANDARD", modelId(jdbc, "SEEDANCE_2_0_STANDARD"),
            "SEEDANCE_2_5", modelId(jdbc, "SEEDANCE_2_5")
        );

        jdbc.update("""
            insert into project
              (id, tenant_id, name, code, owner_id, status, created_by, created_at, updated_at)
            values (91001, 91000, 'Seedance migration project', 'SEEDANCE-MIGRATION', 91002,
                    'ACTIVE', 91002, current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into storyboard
              (id, tenant_id, project_id, episode_no, shot_no, visual_description,
               video_prompt, prompt_document_json, status, created_by, created_at, updated_at)
            values
              (91101, 91000, 91001, 1, 1, 'version one visual', 'keep version one plain prompt',
               '{"version":1,"nodes":[{"type":"text","text":"legacy"}]}',
               'DRAFT', 91002, current_timestamp, current_timestamp),
              (91102, 91000, 91001, 1, 2, 'version two visual', 'keep version two plain prompt',
               '{"version":2,"nodes":[{"type":"text","text":"current"}]}',
               'DRAFT', 91002, current_timestamp, current_timestamp)
            """);
        Long historicalModelId = modelIds.get("SEEDANCE_2_0_FAST");
        jdbc.update("""
            insert into ai_video_task
              (id, tenant_id, project_id, storyboard_id, model_id, provider_code,
               model, prompt, first_frame_url, duration_seconds, aspect_ratio, resolution,
               status, created_by, created_at, updated_at)
            values (91201, 91000, 91001, 91101, ?, 'VOLCENGINE_ARK',
                    'seedance-history', 'historical prompt', '/history/first.png', 8,
                    '16:9', '720p', 'SUCCEEDED', 91002, current_timestamp, current_timestamp)
            """, historicalModelId);
        jdbc.update("""
            insert into ai_video_result
              (id, tenant_id, project_id, task_id, storyboard_id, video_url, storage_path,
               duration_seconds, width, height, file_size, format, is_selected, status,
               created_at, updated_at)
            values (91202, 91000, 91001, 91201, 91101, '/history/video.mp4',
                    'history/video.mp4', 8, 1280, 720, 123456, 'mp4', true, 'ACTIVE',
                    current_timestamp, current_timestamp)
            """);
        jdbc.update("""
            insert into ai_model_price_version
              (id, model_id, version_no, status, effective_from, published_at, created_at)
            values (91203, ?, 91, 'PUBLISHED', timestamp '2026-01-01 00:00:00',
                    timestamp '2025-12-01 00:00:00', timestamp '2025-12-01 00:00:00')
            """, historicalModelId);
        jdbc.update("""
            insert into ai_model_point_price_version
              (id, model_id, version_no, status, effective_from, published_at, created_at)
            values (91204, ?, 92, 'PUBLISHED', timestamp '2026-01-01 00:00:00',
                    timestamp '2025-12-01 00:00:00', timestamp '2025-12-01 00:00:00')
            """, historicalModelId);
        jdbc.update("""
            insert into ai_call_log
              (id, tenant_id, user_id, provider, service_type, model, business_scene,
               request_summary, response_summary, status, duration_ms, created_at)
            values (91205, 91000, 91002, 'VOLCENGINE_ARK', 'VIDEO', 'seedance-history',
                    'ai_video_generate', 'historical request', 'historical response',
                    'SUCCESS', 321, timestamp '2026-01-01 00:00:00')
            """);
        jdbc.update("""
            insert into point_ledger
              (id, tenant_id, user_id, business_type, business_id, entry_type, amount,
               available_balance_after, reserved_balance_after, idempotency_key,
               description, created_at)
            values (91206, 91000, 91002, 'AI_VIDEO_TASK', 91201, 'SETTLE', 2,
                    98, 0, 'seedance-v118-ledger', 'historical settlement',
                    timestamp '2026-01-01 00:00:00')
            """);

        Map<String, Object> taskBefore = jdbc.queryForMap("""
            select tenant_id, project_id, storyboard_id, provider_code, model, prompt,
                   duration_seconds, aspect_ratio, resolution, status
              from ai_video_task where id = 91201
            """);
        Map<String, Object> resultBefore = jdbc.queryForMap("""
            select tenant_id, project_id, task_id, storyboard_id, video_url, storage_path,
                   duration_seconds, width, height, file_size, format, is_selected, status
              from ai_video_result where id = 91202
            """);
        Map<String, Object> costPriceBefore = jdbc.queryForMap(
            "select model_id, version_no, status, effective_from, published_at from ai_model_price_version where id = 91203");
        Map<String, Object> pointPriceBefore = jdbc.queryForMap(
            "select model_id, version_no, status, effective_from, published_at from ai_model_point_price_version where id = 91204");
        Map<String, Object> callLogBefore = jdbc.queryForMap("""
            select tenant_id, user_id, provider, service_type, model, business_scene,
                   request_summary, response_summary, status, duration_ms
              from ai_call_log where id = 91205
            """);
        Map<String, Object> ledgerBefore = jdbc.queryForMap("""
            select tenant_id, user_id, business_type, business_id, entry_type, amount,
                   available_balance_after, reserved_balance_after, idempotency_key, description
              from point_ledger where id = 91206
            """);

        migrate(dataSource, null);

        assertThat(latestVersion(jdbc)).isEqualTo("119");
        modelIds.forEach((code, id) -> assertThat(modelId(jdbc, code)).isEqualTo(id));
        assertThat(jdbc.queryForMap("""
            select tenant_id, project_id, storyboard_id, provider_code, model, prompt,
                   duration_seconds, aspect_ratio, resolution, status
              from ai_video_task where id = 91201
            """)).containsExactlyInAnyOrderEntriesOf(taskBefore);
        assertThat(jdbc.queryForMap("""
            select tenant_id, project_id, task_id, storyboard_id, video_url, storage_path,
                   duration_seconds, width, height, file_size, format, is_selected, status
              from ai_video_result where id = 91202
            """)).containsExactlyInAnyOrderEntriesOf(resultBefore);
        assertThat(jdbc.queryForMap(
            "select model_id, version_no, status, effective_from, published_at from ai_model_price_version where id = 91203"))
            .containsExactlyInAnyOrderEntriesOf(costPriceBefore);
        assertThat(jdbc.queryForMap(
            "select model_id, version_no, status, effective_from, published_at from ai_model_point_price_version where id = 91204"))
            .containsExactlyInAnyOrderEntriesOf(pointPriceBefore);
        assertThat(jdbc.queryForMap("""
            select tenant_id, user_id, provider, service_type, model, business_scene,
                   request_summary, response_summary, status, duration_ms
              from ai_call_log where id = 91205
            """)).containsExactlyInAnyOrderEntriesOf(callLogBefore);
        assertThat(jdbc.queryForMap("""
            select tenant_id, user_id, business_type, business_id, entry_type, amount,
                   available_balance_after, reserved_balance_after, idempotency_key, description
              from point_ledger where id = 91206
            """)).containsExactlyInAnyOrderEntriesOf(ledgerBefore);
        assertThat(jdbc.queryForObject(
            "select prompt_document_json from storyboard where id = 91101", String.class)).isNull();
        assertThat(jdbc.queryForObject(
            "select video_prompt from storyboard where id = 91101", String.class))
            .isEqualTo("keep version one plain prompt");
        assertThat(jdbc.queryForObject(
            "select prompt_document_json from storyboard where id = 91102", String.class))
            .contains("\"version\":2");
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

    private static Long modelId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("select id from ai_model where code = ?", Long.class, code);
    }

    private static String latestVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
            "select version from flyway_schema_history where version is not null order by installed_rank desc limit 1",
            String.class);
    }

    private static int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject(
            "select count(*) from information_schema.tables where lower(table_name) = ?", Integer.class, table);
    }

    private static int columnCount(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject(
            "select count(*) from information_schema.columns where lower(table_name) = ? and lower(column_name) = ?",
            Integer.class, table, column);
    }
}
