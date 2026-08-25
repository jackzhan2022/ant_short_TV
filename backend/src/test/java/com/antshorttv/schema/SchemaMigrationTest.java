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
    void flywayCreatesRevocableSessionAndPlatformAuthorizationSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in (
              'auth_session', 'platform_role', 'platform_permission',
              'platform_role_permission', 'platform_user_role'
            )
            """, Integer.class);
        Integer tokenVersionColumn = jdbc.queryForObject("""
            select count(*)
            from information_schema.columns
            where lower(table_name) = 'app_user' and lower(column_name) = 'token_version'
            """, Integer.class);
        Integer sessionIndexes = jdbc.queryForObject("""
            select count(distinct lower(index_name))
            from information_schema.indexes
            where lower(table_name) = 'auth_session'
              and lower(index_name) in (
                'idx_auth_session_user_status', 'idx_auth_session_expires_at'
              )
            """, Integer.class);
        Integer tokenHashUniqueConstraint = jdbc.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where lower(table_name) = 'auth_session'
              and constraint_type = 'UNIQUE'
              and lower(constraint_name) = 'uk_auth_session_token_hash'
            """, Integer.class);

        assertThat(tableCount).isEqualTo(5);
        assertThat(tokenVersionColumn).isEqualTo(1);
        assertThat(sessionIndexes).isEqualTo(2);
        assertThat(tokenHashUniqueConstraint).isEqualTo(1);
    }

    @Test
    void flywayCreatesAccountTeamTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in (
              'app_user', 'tenant', 'tenant_member', 'tenant_invitation', 'operation_log',
              'role', 'permission', 'role_permission', 'member_role',
              'project', 'project_member',
              'project_role', 'project_role_permission', 'project_operation_log',
              'team_point_account', 'team_point_transaction'
            )
            """, Integer.class);

        assertThat(tableCount).isEqualTo(16);
    }

    @Test
    void flywayRemovesOrganizationStorageAndProjectDataScope() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer organizationTables = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in ('organization', 'organization_member')
            """, Integer.class);
        Integer removedColumns = jdbc.queryForObject("""
            select count(*)
            from information_schema.columns
            where (lower(table_name) = 'project' and lower(column_name) = 'organization_id')
               or (lower(table_name) = 'project_member' and lower(column_name) = 'organization_id')
               or (lower(table_name) = 'project_role' and lower(column_name) = 'data_scope')
            """, Integer.class);

        assertThat(organizationTables).isZero();
        assertThat(removedColumns).isZero();
    }

    @Test
    void flywayAddsOptionalMainProjectBindingToReviewDrafts() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer nullableBinding = jdbc.queryForObject("""
            select case when is_nullable = 'YES' then 1 else 0 end
            from information_schema.columns
            where lower(table_name) = 'review_project'
              and lower(column_name) = 'main_project_id'
            """, Integer.class);
        Integer bindingIndex = jdbc.queryForObject("""
            select count(distinct lower(index_name))
            from information_schema.indexes
            where lower(table_name) = 'review_project'
              and lower(index_name) = 'idx_review_project_tenant_main_project'
            """, Integer.class);

        assertThat(nullableBinding).isEqualTo(1);
        assertThat(bindingIndex).isEqualTo(1);
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
    void flywayCreatesProjectMetadataColumns() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer columnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'project'
              and lower(column_name) in (
                'aspect_ratio', 'file_format', 'script_type',
                'breakdown_strength', 'cover_source', 'visual_style',
                'initial_script_content'
              )
            """, Integer.class);

        assertThat(columnCount).isEqualTo(7);
    }

    @Test
    void flywayCreatesScriptAnalysisPipelineTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in (
              'script_analysis_task',
              'script_analysis_stage',
              'script_analysis_result'
            )
            """, Integer.class);
        Integer taskColumnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'script_analysis_task'
              and lower(column_name) in (
                'tenant_id', 'project_id', 'script_version_id',
                'status', 'overall_progress', 'current_stage',
                'idempotency_key'
              )
            """, Integer.class);
        Integer resultColumnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'script_analysis_result'
              and lower(column_name) in (
                'raw_response', 'normalized_json', 'provider_request_id',
                'ai_call_log_id', 'duration_ms', 'error_code',
                'error_message', 'retryable'
              )
            """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
        assertThat(taskColumnCount).isEqualTo(7);
        assertThat(resultColumnCount).isEqualTo(8);
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

    @Test
    void flywayCreatesUnifiedAiExecutionSchemaAndCorrelations() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in ('ai_execution_task', 'ai_execution_attempt')
            """, Integer.class);
        Integer taskColumnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'ai_execution_task'
              and lower(column_name) in (
                'tenant_id', 'user_id', 'project_id', 'scene', 'capability',
                'business_type', 'business_id', 'requested_model_id', 'resolved_model_id',
                'status', 'phase', 'progress', 'execution_version', 'client_idempotency_key',
                'trace_id', 'claim_token', 'claimed_at', 'heartbeat_at', 'claim_expires_at',
                'retryable', 'result_type', 'result_id', 'error_code', 'error_message',
                'usage_cost_status', 'point_settlement_status', 'created_at', 'updated_at'
              )
            """, Integer.class);
        Integer attemptColumnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'ai_execution_attempt'
              and lower(column_name) in (
                'execution_id', 'execution_version', 'phase', 'attempt_no', 'status',
                'idempotency_key', 'provider_contacted', 'provider_id', 'model_id',
                'provider_request_id', 'external_task_id', 'ai_call_log_id',
                'retryable', 'error_code', 'error_message', 'started_at', 'finished_at'
              )
            """, Integer.class);
        Integer eligibilityIndexes = jdbc.queryForObject("""
            select count(distinct lower(index_name))
            from information_schema.indexes
            where lower(table_name) = 'ai_execution_task'
              and lower(index_name) in (
                'idx_ai_execution_task_eligibility',
                'idx_ai_execution_task_tenant_running'
              )
            """, Integer.class);
        Integer uniqueConstraints = jdbc.queryForObject("""
            select count(distinct lower(constraint_name))
            from information_schema.table_constraints
            where constraint_type = 'UNIQUE'
              and (
                (lower(table_name) = 'ai_execution_task'
                  and lower(constraint_name) = 'uk_ai_execution_task_idempotency')
                or
                (lower(table_name) = 'ai_execution_attempt'
                  and lower(constraint_name) = 'uk_ai_execution_attempt_idempotency')
              )
            """, Integer.class);
        Integer correlationColumns = jdbc.queryForObject("""
            select count(*)
            from information_schema.columns
            where (lower(table_name) = 'ai_call_log'
                    and lower(column_name) in ('execution_id', 'attempt_id', 'execution_version', 'phase'))
               or (lower(table_name) = 'ai_image_task' and lower(column_name) = 'execution_id')
               or (lower(table_name) = 'ai_image_result' and lower(column_name) = 'execution_id')
               or (lower(table_name) = 'ai_video_task' and lower(column_name) = 'execution_id')
               or (lower(table_name) = 'ai_video_result' and lower(column_name) = 'execution_id')
               or (lower(table_name) = 'script_analysis_task' and lower(column_name) = 'execution_id')
               or (lower(table_name) = 'script_analysis_result' and lower(column_name) = 'execution_id')
               or (lower(table_name) = 'review_task' and lower(column_name) = 'execution_id')
            """, Integer.class);

        assertThat(tableCount).isEqualTo(2);
        assertThat(taskColumnCount).isEqualTo(28);
        assertThat(attemptColumnCount).isEqualTo(17);
        assertThat(eligibilityIndexes).isEqualTo(2);
        assertThat(uniqueConstraints).isEqualTo(2);
        assertThat(correlationColumns).isEqualTo(11);
    }

    @Test
    void flywayCreatesDurableScriptAiOperationOwnership() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(*)
            from information_schema.tables
            where lower(table_name) = 'script_ai_operation'
            """, Integer.class);
        Integer columnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'script_ai_operation'
              and lower(column_name) in (
                'tenant_id', 'project_id', 'operation_type', 'script_id',
                'script_version_id', 'redacted_input_json', 'idempotency_key',
                'status', 'execution_id', 'result_type', 'result_id',
                'error_code', 'error_message', 'created_by', 'created_at',
                'updated_at', 'completed_at'
              )
            """, Integer.class);
        Integer indexCount = jdbc.queryForObject("""
            select count(distinct lower(index_name))
            from information_schema.indexes
            where lower(table_name) = 'script_ai_operation'
              and lower(index_name) in (
                'idx_script_ai_operation_execution',
                'idx_script_ai_operation_project'
              )
            """, Integer.class);
        Integer uniqueConstraintCount = jdbc.queryForObject("""
            select count(*)
            from information_schema.table_constraints
            where lower(table_name) = 'script_ai_operation'
              and lower(constraint_name) = 'uk_script_ai_operation_idempotency'
              and constraint_type = 'UNIQUE'
            """, Integer.class);
        Integer scriptVersionExecutionColumns = jdbc.queryForObject("""
            select count(*)
            from information_schema.columns
            where lower(table_name) = 'script_version'
              and lower(column_name) = 'execution_id'
            """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(columnCount).isEqualTo(17);
        assertThat(indexCount).isEqualTo(2);
        assertThat(uniqueConstraintCount).isEqualTo(1);
        assertThat(scriptVersionExecutionColumns).isEqualTo(1);
    }

    @Test
    void flywaySeedsFixedOnePointCompatibilityMetadata() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer compatibilitySceneCount = jdbc.queryForObject("""
            select count(*)
            from ai_execution_scene_compatibility
            where enabled = true
              and point_charge_mode = 'FIXED'
              and point_amount = 1
            """, Integer.class);
        Integer legacySceneCount = jdbc.queryForObject("""
            select count(*)
            from ai_execution_scene_compatibility
            where scene in (
              'CHARACTER', 'SCENE', 'STORYBOARD_FIRST_FRAME',
              'STORYBOARD_VIDEO_GENERATION', 'AI_VOICE_SYNTHESIS',
              'script_generate', 'script_rewrite', 'script_global_understanding',
              'script_episode_split', 'script_episode_summary',
              'script_character_scene_recognition', 'character_extract', 'scene_extract',
              'prop_extract', 'storyboard_breakdown', 'prompt_generate',
              'video_understanding', 'video_script_draft', 'script_review'
            )
            """, Integer.class);

        assertThat(compatibilitySceneCount).isEqualTo(19);
        assertThat(legacySceneCount).isEqualTo(19);
    }

    @Test
    void flywayCreatesReviewExecutionPolicyAndUniqueDomainLink() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer policyCount = jdbc.queryForObject("""
            select count(*)
              from ai_point_policy_version
             where scene = 'script_review'
               and capability = 'TEXT'
               and version_no = 1
               and status = 'PUBLISHED'
               and charge_business_failure = true
            """, Integer.class);
        Integer fixedComponentCount = jdbc.queryForObject("""
            select count(*)
              from ai_point_policy_component component
              join ai_point_policy_version policy on policy.id = component.policy_version_id
             where policy.scene = 'script_review'
               and policy.version_no = 1
               and component.metric = 'FIXED_EXECUTION'
               and component.unit_size = 1
               and component.point_rate = 1
            """, Integer.class);
        Integer uniqueExecutionIndexCount = jdbc.queryForObject("""
            select count(distinct lower(index_name))
              from information_schema.indexes
             where lower(table_name) = 'review_task'
               and lower(index_name) = 'uk_review_task_execution'
               and index_type_name = 'UNIQUE INDEX'
            """, Integer.class);

        assertThat(policyCount).isEqualTo(1);
        assertThat(fixedComponentCount).isEqualTo(1);
        assertThat(uniqueExecutionIndexCount).isEqualTo(1);
    }

    @Test
    void flywayCreatesAndSeedsPublicStyleLibrary() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) = 'style_library'
            """, Integer.class);
        Integer columnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'style_library'
              and lower(column_name) in (
                'external_id', 'name', 'category', 'description',
                'source_image_url', 'storage_path', 'image_url',
                'image_width', 'image_height', 'is_public', 'sort_order'
              )
            """, Integer.class);
        Integer styleCount = jdbc.queryForObject("select count(*) from style_library", Integer.class);
        String category = jdbc.queryForObject(
            "select category from style_library where external_id = '864621266010645040'",
            String.class
        );
        String storagePath = jdbc.queryForObject(
            "select storage_path from style_library where external_id = '864621266010645040'",
            String.class
        );
        String imageUrl = jdbc.queryForObject(
            "select image_url from style_library where external_id = '864621266010645040'",
            String.class
        );

        assertThat(tableCount).isEqualTo(1);
        assertThat(columnCount).isEqualTo(11);
        assertThat(styleCount).isEqualTo(139);
        assertThat(category).isEqualTo("3D风格");
        assertThat(storagePath).isEqualTo("style-library/public/864621266010645040/cover-compressed.jpg");
        assertThat(imageUrl).isEqualTo("/style-library/public/864621266010645040/cover-compressed.jpg");
    }

    @Test
    void flywayCreatesPublicInspirationGalleryTable() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) = 'inspiration_creation'
            """, Integer.class);
        Integer columnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
            from information_schema.columns
            where lower(table_name) = 'inspiration_creation'
              and lower(column_name) in (
                'external_id', 'external_task_id', 'creation_type', 'task_type',
                'title', 'author_name', 'url', 'storage_path', 'mime_type',
                'file_size', 'detail_json', 'source_created_at', 'source_updated_at',
                'import_status', 'import_error', 'sort_order', 'created_at', 'updated_at'
              )
            """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(columnCount).isEqualTo(18);
    }

    @Test
    void flywayAllowsVideoDecompositionToBeTenantScopedBeforeProjectBinding() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer batchProjectNullable = jdbc.queryForObject("""
            select case when is_nullable = 'YES' then 1 else 0 end
            from information_schema.columns
            where lower(table_name) = 'video_decomposition_batch'
              and lower(column_name) = 'project_id'
            """, Integer.class);
        Integer episodeProjectNullable = jdbc.queryForObject("""
            select case when is_nullable = 'YES' then 1 else 0 end
            from information_schema.columns
            where lower(table_name) = 'video_decomposition_episode'
              and lower(column_name) = 'project_id'
            """, Integer.class);

        assertThat(batchProjectNullable).isEqualTo(1);
        assertThat(episodeProjectNullable).isEqualTo(1);
    }

    @Test
    void flywayAddsExecutionRegenerationLineage() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer columnCount = jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'ai_execution_task'
               and lower(column_name) in ('source_execution_id', 'root_execution_id')
            """, Integer.class);
        Integer indexCount = jdbc.queryForObject("""
            select count(distinct lower(index_name))
              from information_schema.indexes
             where lower(table_name) = 'ai_execution_task'
               and lower(index_name) = 'uk_ai_execution_task_root_version'
            """, Integer.class);

        assertThat(columnCount).isEqualTo(2);
        assertThat(indexCount).isEqualTo(1);
    }
}
