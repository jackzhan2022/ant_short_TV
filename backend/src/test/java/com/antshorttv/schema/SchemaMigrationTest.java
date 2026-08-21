package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesAccountTeamTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in (
              'app_user', 'tenant', 'tenant_member', 'tenant_invitation', 'operation_log',
              'role', 'permission', 'role_permission', 'member_role',
              'organization', 'organization_member', 'project', 'project_member',
              'project_role', 'project_role_permission', 'project_operation_log',
              'team_point_account', 'team_point_transaction'
            )
            """, Integer.class);

        assertThat(tableCount).isEqualTo(18);
    }

    @Test
    void flywayCreatesAiServiceTablesAndProviders() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in (
              'ai_provider', 'ai_service_config', 'ai_service_test_log', 'ai_call_log'
            )
            """, Integer.class);
        Integer providerCount = jdbc.queryForObject("""
            select count(*)
            from ai_provider
            where code in ('OpenAI', 'Gemini', '火山', 'MiniMax')
            """, Integer.class);

        assertThat(tableCount).isEqualTo(4);
        assertThat(providerCount).isEqualTo(4);
    }

    @Test
    void aiServiceConfigKeepsOneActiveDefaultPerServiceTypeGlobally() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("delete from ai_service_config");
        jdbc.update("""
            insert into ai_service_config
              (tenant_id, provider, service_type, name, base_url, api_key_cipher, model,
               priority, is_default, enabled, last_test_status, created_by, created_at, updated_at)
            values
              (100, 'OpenAI', 'TEXT', 'OpenAI 默认', 'https://example.com', 'cipher-1',
               'model-a', 100, true, true, 'UNTESTED', 1, now(), now())
            """);

        assertThatThrownBy(() -> jdbc.update("""
            insert into ai_service_config
              (tenant_id, provider, service_type, name, base_url, api_key_cipher, model,
               priority, is_default, enabled, last_test_status, created_by, created_at, updated_at)
            values
              (101, 'Gemini', 'TEXT', 'Gemini 默认', 'https://example.com', 'cipher-2',
               'model-b', 90, true, true, 'UNTESTED', 1, now(), now())
            """)).isInstanceOf(Exception.class);
    }

    @Test
    void flywayCreatesAiVideoTaskTablesAndStoryboardVideoColumns() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in (
              'ai_video_task', 'ai_video_result', 'material'
            )
            """, Integer.class);
        Integer storyboardColumnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'storyboard'
              and lower(column_name) in (
                'first_frame_url', 'current_video_result_id', 'current_video_url'
              )
            """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
        assertThat(storyboardColumnCount).isEqualTo(3);
    }

    @Test
    void scriptContentColumnsAcceptLongDrafts() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String longContent = "剧本正文".repeat(10000);

        jdbc.update("""
            insert into script
              (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at)
            values
              (9001, 9002, '长剧本', 'MANUAL_EDIT', ?, 'DRAFT', 9003, now(), now())
            """, longContent);
        Long scriptId = jdbc.queryForObject("select id from script where tenant_id = 9001", Long.class);

        jdbc.update("""
            insert into script_version
              (tenant_id, project_id, script_id, version_no, source_type, input_summary, content, status, created_by, created_at)
            values
              (9001, 9002, ?, 1, 'MANUAL_EDIT', ?, ?, 'DRAFT', 9003, now())
            """, scriptId, longContent, longContent);

        Integer storedLength = jdbc.queryForObject(
            "select length(content) from script where id = ?",
            Integer.class,
            scriptId
        );

        assertThat(storedLength).isEqualTo(longContent.length());
    }

    @Test
    void flywayCreatesAiTaskExecutionReliabilityMetadata() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer decompositionEpisodeColumns = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'video_decomposition_episode'
              and lower(column_name) in (
                'execution_token', 'execution_phase', 'execution_version',
                'claimed_at', 'heartbeat_at', 'execution_timeout_at', 'retryable'
              )
            """, Integer.class);
        Integer decompositionAttemptColumns = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'video_decomposition_attempt'
              and lower(column_name) in ('idempotency_key', 'retryable')
            """, Integer.class);
        Integer aiVideoTaskColumns = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'ai_video_task'
              and lower(column_name) in (
                'execution_token', 'execution_phase', 'execution_version',
                'claimed_at', 'heartbeat_at', 'execution_timeout_at', 'retryable'
              )
            """, Integer.class);
        Integer aiVideoAttemptTableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) = 'ai_video_task_attempt'
            """, Integer.class);

        assertThat(decompositionEpisodeColumns).isEqualTo(7);
        assertThat(decompositionAttemptColumns).isEqualTo(2);
        assertThat(aiVideoTaskColumns).isEqualTo(7);
        assertThat(aiVideoAttemptTableCount).isEqualTo(1);
    }
}
