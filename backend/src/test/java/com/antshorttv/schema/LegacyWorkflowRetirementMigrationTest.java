package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import db.migration.V114__retire_legacy_workflow_data;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LegacyWorkflowRetirementMigrationTest {
    @Test
    void preservesCurrentWorkAndSettledAuditWhileReleasingOnlyLegacyRemaindersIdempotently() throws Exception {
        DriverManagerDataSource source = database("mixed");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedMixed(jdbc);
        Map<String, Object> settled = jdbc.queryForMap("select * from ai_point_reservation where id=2");
        Map<String, Object> current = jdbc.queryForMap("select * from ai_execution_task where id=3");
        Map<String, Object> agent = jdbc.queryForMap("select * from ai_workflow_agent where code='script-review'");
        Map<String, Object> customAgent = jdbc.queryForMap("select * from ai_workflow_agent where code='custom-review'");
        try (Connection connection = source.getConnection()) {
            V114__retire_legacy_workflow_data.cleanup(connection);
            V114__retire_legacy_workflow_data.cleanup(connection);
        }
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=9001", Integer.class)).isEqualTo(85);
        assertThat(jdbc.queryForObject("select reserved_balance from team_point_account where tenant_id=9001", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("select total_consumed from team_point_account where tenant_id=9001", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select sum(amount) from point_ledger where entry_type='RELEASE'", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("select count(*) from point_ledger", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForMap("select * from ai_point_reservation where id=2")).isEqualTo(settled);
        assertThat(jdbc.queryForMap("select * from ai_execution_task where id=3")).isEqualTo(current);
        assertThat(jdbc.queryForMap("select * from ai_workflow_agent where code='script-review'")).isEqualTo(agent);
        assertThat(jdbc.queryForMap("select * from ai_workflow_agent where code='custom-review'")).isEqualTo(customAgent);
        assertThat(jdbc.queryForList("select tool_code from ai_workflow_agent_tool where agent_id=9008", String.class))
            .containsExactly("read_review_context");
        assertThat(jdbc.queryForObject("select count(*) from ai_workflow_agent_skill where agent_id=9008", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select tool_code from ai_workflow_agent_tool where agent_id=9007", String.class))
            .containsExactly("read_review_content");
        assertThat(jdbc.queryForList("select skill_code from ai_workflow_agent_skill where agent_id=9007", String.class))
            .containsExactly("script-review-foundation");
        assertThat(jdbc.queryForList("select id from script_analysis_task order by id", Long.class)).containsExactly(5L);
        assertThat(jdbc.queryForList("select id from review_task order by id", Long.class)).containsExactly(7L);
        assertThat(jdbc.queryForList("select task_id from review_fanout_snapshot", Long.class)).containsExactly(7L);
        assertThat(jdbc.queryForObject("select count(*) from review_unit_result", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select report_markdown from review_unit_result", String.class)).isEqualTo("# current fragment");
        assertThat(jdbc.queryForObject("select last_task_id from review_project where id=9005", Long.class)).isNull();
        assertThat(jdbc.queryForObject("select released_points from ai_point_reservation where id=1", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select settled_points from ai_point_reservation where id=1", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select provider_contacted from ai_execution_attempt where id=1", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select snapshot_json from script_analysis_config_snapshot where task_id=5", String.class))
            .isEqualTo("{\"modelId\":1}");
        assertThat(jdbc.queryForObject("select content_json from script_episode_summary where id=9010", String.class)).contains("current summary");
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_task", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_attempt", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select status from script_ai_operation where id=1", String.class)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("select status from script_ai_operation where id=3", String.class)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForList("select id from ai_execution_task where status='CANCELED' order by id", Long.class))
            .containsExactly(1L, 4L, 6L, 8L);
        assertThat(jdbc.queryForObject("select count(*) from point_ledger where reservation_id is null or execution_id is null or user_id is null or attempt_id is null or description is null", Integer.class)).isZero();
        migrate(source, null);
        assertRetiredSchema(jdbc);
        assertThat(jdbc.queryForObject("select count(*) from point_ledger", Integer.class)).isEqualTo(4);
    }

    @Test
    void cancelsOnlyPreciselyScopedLegacyReviewRunsAndUnfinishedSteps() throws Exception {
        DriverManagerDataSource source = database("review_runs");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedMixed(jdbc);
        for (long id = 9200; id <= 9206; id++) {
            jdbc.update("""
                insert into ai_workflow_agent_run (id,agent_code,run_type,tenant_id,user_id,project_id,task_id,
                    status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at)
                values (?,?,?, ?,1,?,?,'RUNNING',1,0.1,4096,20,'preserved prompt',now(),now())
                """, id, id == 9205 ? "short-drama-asset-recognition" : "script-review",
                id == 9201 ? "CUSTOM_CHILD" : "FORMAL", id == 9203 ? 9999 : 9001,
                id == 9204 ? 9002 : 9005, id == 9202 ? 7 : 6);
            jdbc.update("""
                insert into ai_workflow_agent_run_step (run_id,step_no,step_type,status,input_json,output_json,started_at,created_at)
                values (?,1,'MODEL','RUNNING','input evidence','output evidence',now(),now()),
                       (?,2,'TOOL','SUCCESS','terminal input','terminal output',now(),now())
                """, id, id);
        }
        jdbc.update("update review_fanout_unit set child_run_id=9201 where id=6");
        // A corrupt link must not pull another tenant's run into the retirement scope.
        jdbc.update("update review_task set aggregation_run_id=9203 where id=6");
        jdbc.update("update ai_workflow_agent_run set status='FAILED',error_code='ORIGINAL' where id=9206");
        var protectedRuns = jdbc.queryForList("select * from ai_workflow_agent_run where id in (9106,9107,9202,9203,9204,9205,9206) order by id");
        var terminalSteps = jdbc.queryForList("select * from ai_workflow_agent_run_step where step_no=2 order by id");
        try (Connection connection = source.getConnection()) {
            V114__retire_legacy_workflow_data.cleanup(connection);
            V114__retire_legacy_workflow_data.cleanup(connection);
        }
        assertThat(jdbc.queryForList("select id from ai_workflow_agent_run where status='CANCELED' order by id", Long.class))
            .containsExactly(9200L, 9201L);
        assertThat(jdbc.queryForList("select run_id from ai_workflow_agent_run_step where status='CANCELED' order by run_id", Long.class))
            .containsExactly(9200L, 9201L, 9206L);
        assertThat(jdbc.queryForObject("select count(*) from ai_workflow_agent_run where status='CANCELED' and finished_at is null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_workflow_agent_run_step where status='CANCELED' and (finished_at is null or input_json<>'input evidence' or output_json<>'output evidence')", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select * from ai_workflow_agent_run where id in (9106,9107,9202,9203,9204,9205,9206) order by id")).isEqualTo(protectedRuns);
        assertThat(jdbc.queryForList("select * from ai_workflow_agent_run_step where step_no=2 order by id")).isEqualTo(terminalSteps);
        assertThat(jdbc.queryForObject("select count(*) from ai_workflow_agent_run_step where run_id in (9202,9203,9204,9205) and status='RUNNING'", Integer.class)).isEqualTo(4);
    }

    @Test
    void retiresEveryLegacyRegenerationAndClearsFailedRetrySchedules() throws Exception {
        DriverManagerDataSource source = database("regenerations");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedMixed(jdbc);
        for (long id = 20; id <= 22; id++) {
            jdbc.update("""
                insert into ai_execution_task (id,tenant_id,user_id,project_id,scene,capability,business_type,business_id,
                    execution_version,status,phase,retryable,next_run_at,client_idempotency_key,trace_id,created_at,updated_at)
                values (?,9001,1,9002,?,'TEXT',?,?,?,?,'MODEL',true,now(),?,?,now(),now())
                """, id, id == 20 ? "scene_extract" : "script_review", id == 20 ? "SCRIPT_AI_OPERATION" : "REVIEW_TASK",
                id == 20 ? 999 : 6, id - 18, id == 22 ? "FAILED" : "RUNNING", "extra-" + id, "trace-" + id);
            jdbc.update("""
                insert into ai_execution_attempt (execution_id,execution_version,phase,attempt_no,status,retryable,next_retry_at,idempotency_key,started_at)
                values (?,?,'MODEL',1,?,true,now(),?,now())
                """, id, id - 18, id == 22 ? "FAILED" : "STARTED", "extra-attempt-" + id);
        }
        try (Connection connection = source.getConnection()) {
            V114__retire_legacy_workflow_data.cleanup(connection);
        }
        assertThat(jdbc.queryForList("select status from ai_execution_task where id>=20 order by id", String.class))
            .containsExactly("CANCELED", "CANCELED", "FAILED");
        assertThat(jdbc.queryForList("select status from ai_execution_attempt where execution_id>=20 order by execution_id", String.class))
            .containsExactly("CANCELED", "CANCELED", "FAILED");
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_task where id>=20 and (retryable=true or next_run_at is not null)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_attempt where execution_id>=20 and (retryable=true or next_retry_at is not null)", Integer.class)).isZero();
    }

    @Test
    void rollsBackAllCancellationAndFundsWhenAnAccountCannotCoverItsRecordedReservation() throws Exception {
        DriverManagerDataSource source = database("rollback");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedMixed(jdbc);
        jdbc.update("update team_point_account set reserved_balance=1 where tenant_id=9001");
        try (Connection connection = source.getConnection()) {
            assertThatThrownBy(() -> V114__retire_legacy_workflow_data.cleanup(connection))
                .isInstanceOf(IllegalStateException.class);
        }
        assertThat(jdbc.queryForObject("select status from ai_execution_task where id=1", String.class)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where id=1", String.class)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select count(*) from point_ledger", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from review_task", Integer.class)).isEqualTo(2);
    }

    @Test
    void migratesAnEmptyDatabaseToTheSinglePathSchema() {
        DriverManagerDataSource source = database("empty");
        migrate(source, null);
        assertRetiredSchema(new JdbcTemplate(source));
    }

    @Test
    void preservesLegacyMarkedTasksWithAFrozenFanoutEvenBeforeAnyRunExists() throws Exception {
        DriverManagerDataSource source = database("fanout");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedMixed(jdbc);
        jdbc.update("delete from ai_workflow_agent_run where analysis_stage_id=5");
        jdbc.update("""
            insert into script_analysis_fanout_snapshot
              (tenant_id,project_id,script_id,task_id,stage_id,stage_code,attempt_no,
               agent_code,agent_revision,model_id,episode_set_hash,status,total_units,created_at,updated_at)
            values (9001,9002,9003,5,5,'EPISODE_SUMMARY',1,'short-drama-episode-summary',1,1,
                    'frozen','PENDING',1,now(),now())
            """);
        try (Connection connection = source.getConnection()) {
            V114__retire_legacy_workflow_data.cleanup(connection);
        }
        assertThat(jdbc.queryForObject("select status from script_analysis_task where id=5", String.class)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where id=5", String.class)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select count(*) from script_analysis_fanout_snapshot where task_id=5", Integer.class)).isOne();
    }

    @Test
    void rejectsAnInvalidCurrentModelSnapshotWithoutInventingAReplacement() throws Exception {
        DriverManagerDataSource source = database("invalid_snapshot");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        seedMixed(jdbc);
        jdbc.update("update script_analysis_config_snapshot set snapshot_json='{}' where task_id=5");
        try (Connection connection = source.getConnection()) {
            assertThatThrownBy(() -> V114__retire_legacy_workflow_data.cleanup(connection))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("modelId");
        }
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=9001", Integer.class)).isEqualTo(70);
        assertThat(jdbc.queryForObject("select count(*) from point_ledger", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select snapshot_json from script_analysis_config_snapshot where task_id=5", String.class)).isEqualTo("{}");
    }

    private void assertRetiredSchema(JdbcTemplate jdbc) {
        String schema = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
            "MySQL".equals(connection.getMetaData().getDatabaseProductName())
                ? connection.getCatalog() : connection.getSchema());
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.tables where table_schema=? and lower(table_name) in
              ('review_issue','review_issue_hit','review_issue_event','review_batch_repair',
               'review_pipeline_stage','review_candidate_audit','review_semantic_decision',
               'script_asset_candidate','script_asset_candidate_alias','script_asset_promotion_decision',
               'script_asset_normalization_run','ai_agent_definition','ai_skill_definition','ai_agent_skill')
            """, Integer.class, schema)).isZero();
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.columns
             where table_schema=? and ((lower(table_name)='review_task' and lower(column_name) in ('result_format','result_json','global_index_json'))
                or (lower(table_name)='review_unit_result' and lower(column_name) in ('coverage_json','candidates_json'))
                or (lower(table_name)='script_analysis_task' and lower(column_name)='pipeline_version')
                or (lower(table_name)='script_episode' and lower(column_name)='summary'))
            """, Integer.class, schema)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? and lower(table_name)='review_fanout_unit' and lower(column_name)='report_saved'", Integer.class, schema)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from platform_permission where code in ('PLATFORM_AI_AGENT_VIEW','PLATFORM_AI_AGENT_EDIT','PLATFORM_AI_SKILL_EDIT')", Integer.class)).isZero();
    }

    private void seedMixed(JdbcTemplate jdbc) {
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9001,'retire','Retirement','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9002,9001,'Project','retire',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into script (id,tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at) values (9003,9001,9002,'Script','MANUAL_EDIT','text','ACTIVE',1,now(),now())");
        jdbc.update("insert into script_episode (id,tenant_id,project_id,script_id,stable_key,episode_no,title,summary,content,content_fingerprint,reconciliation_status,status,created_at,updated_at) values (9004,9001,9002,9003,'e1',1,'One','legacy mirror','text','hash','NEW','ACTIVE',now(),now())");
        jdbc.update("insert into script_episode_summary (id,tenant_id,project_id,script_id,episode_id,schema_version,content_json,source,created_by,updated_by,created_at,updated_at) values (9010,9001,9002,9003,9004,1,'{\"summary\":\"current summary\"}','USER',1,1,now(),now())");
        jdbc.update("insert into team_point_account (tenant_id,balance,total_granted,total_consumed,reserved_balance,total_reserved,total_released,total_refunded,version,created_at,updated_at) values (9001,70,110,10,30,42,2,0,0,now(),now())");
        jdbc.update("insert into ai_workflow_agent (id,code,name,system_prompt,model_id,status,revision,created_at,updated_at) values (9007,'script-review','Custom review','Administrator prompt',1,'DISABLED',17,now(),now())");
        jdbc.update("insert into ai_workflow_agent (id,code,name,system_prompt,model_id,status,revision,created_at,updated_at) values (9008,'custom-review','Custom agent','Custom administrator prompt',1,'ENABLED',29,now(),now())");
        jdbc.update("insert into ai_workflow_agent_tool (agent_id,tool_code,created_at) values (9008,'read_review_context',now()),(9008,'save_review_result',now())");
        jdbc.update("insert into ai_workflow_agent_skill (agent_id,skill_code,load_order,created_at) values (9008,'script-review-semantic-quality',1,now())");
        jdbc.update("insert into ai_workflow_agent_tool (agent_id,tool_code,created_at) values (9007,'read_review_content',now()),(9007,'save_review_result',now())");
        jdbc.update("insert into ai_workflow_agent_skill (agent_id,skill_code,load_order,created_at) values (9007,'script-review-foundation',1,now()),(9007,'script-review-semantic-quality',99,now())");
        String[] scenes = {"script_element_extract","character_extract","scoped_asset_reextraction","script_analysis","script_analysis","script_review","script_review","prop_extract"};
        String[] types = {"SCRIPT_AI_OPERATION","SCRIPT_AI_OPERATION","SCRIPT_AI_OPERATION","SCRIPT_ANALYSIS_TASK","SCRIPT_ANALYSIS_TASK","REVIEW_TASK","REVIEW_TASK","SCRIPT_AI_OPERATION"};
        int[] reserved = {10,8,5,4,5,3,5,2};
        int[] settled = {2,8,0,0,0,0,0,0};
        for (int i = 0; i < scenes.length; i++) {
            long id = i + 1;
            String state = id == 2 ? "SUCCEEDED" : "RUNNING";
            jdbc.update("""
                insert into ai_execution_task (id,tenant_id,user_id,project_id,scene,capability,business_type,business_id,status,phase,client_idempotency_key,trace_id,created_at,updated_at)
                values (?,9001,1,9002,?,'TEXT',?,?,?,'MODEL',?,?,now(),now())
                """, id, scenes[i], types[i], id, state, "retire-" + id, "trace-" + id);
            jdbc.update("""
                insert into ai_execution_attempt (id,execution_id,execution_version,phase,attempt_no,status,idempotency_key,started_at)
                values (?,?,1,'MODEL',1,?,?,now())
                """, id, id, id == 2 ? "SUCCEEDED" : "STARTED", "attempt-" + id);
            jdbc.update("""
                insert into ai_point_reservation (id,tenant_id,user_id,execution_id,execution_version,business_type,business_id,scene,status,reserved_points,settled_points,released_points,idempotency_key,created_at,updated_at)
                values (?,9001,1,?,1,?,?,?,?,?,?,?,?,now(),now())
                """, id, id, types[i], id, scenes[i], id == 2 ? "SETTLED" : "RESERVED", reserved[i], settled[i],
                id == 1 ? 2 : 0, "reservation-" + id);
        }
        jdbc.update("update ai_execution_attempt set provider_contacted=true,transport_outcome='SUCCESS',business_outcome='FAILED' where id=1");
        jdbc.update("insert into script_ai_operation (id,tenant_id,project_id,operation_type,script_id,redacted_input_json,idempotency_key,status,execution_id,created_by,created_at,updated_at) values (1,9001,9002,'ELEMENT_EXTRACT',9003,'{}','op1','RUNNING',1,1,now(),now()),(3,9001,9002,'SCOPED_ASSET_REEXTRACTION',9003,'{}','op3','RUNNING',3,1,now(),now())");
        for (long id : new long[] {4,5}) {
            jdbc.update("insert into script_analysis_task (id,tenant_id,project_id,script_id,script_version_id,workflow_code,pipeline_version,status,idempotency_key,created_by,execution_id,created_at,updated_at) values (?,9001,9002,9003,1,'ANALYSIS','LEGACY_V1','RUNNING',?,1,?,now(),now())", id, "analysis-" + id, id);
            jdbc.update("insert into script_analysis_stage (id,task_id,stage_code,stage_order,status,created_at,updated_at) values (?,?,'GLOBAL_UNDERSTANDING',1,'RUNNING',now(),now())", id, id);
            jdbc.update("insert into script_analysis_config_snapshot (task_id,agent_code,snapshot_json,created_at) values (?,'legacy','{\"modelId\":1,\"stages\":{\"legacy\":{}}}',now())", id);
        }
        jdbc.update("insert into ai_workflow_agent_run (agent_code,run_type,tenant_id,user_id,project_id,task_id,analysis_stage_id,status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at) values ('short-drama-global-understanding','FORMAL',9001,1,9002,5,5,'RUNNING',1,0.1,4096,20,'',now(),now())");
        jdbc.update("insert into review_project (id,tenant_id,name,source_type,original_content,last_task_id,status,created_by,created_at,updated_at) values (9005,9001,'Review','TXT','text',6,'ACTIVE',1,now(),now())");
        jdbc.update("insert into review_script_version (id,tenant_id,project_id,version_no,source_type,content,created_by,created_at,updated_at) values (9006,9001,9005,1,'TXT','text',1,now(),now())");
        for (long id : new long[] {6,7}) {
            jdbc.update("insert into review_task (id,tenant_id,project_id,script_version_id,round_no,review_mode,selected_dimensions_json,review_scope_type,result_format,status,idempotency_key,created_by,execution_id,created_at,updated_at) values (?,9001,9005,9006,1,'QUICK','[]','ALL',?,'RUNNING',?,1,?,now(),now())", id, id == 7 ? "MARKDOWN" : "STRUCTURED_JSON", "review-" + id, id);
            jdbc.update("""
                insert into ai_workflow_agent_run (id,agent_code,run_type,tenant_id,user_id,project_id,task_id,
                    status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at)
                values (?,'script-review','REVIEW_CHILD',9001,1,9005,?,'SUCCEEDED',1,0.1,4096,20,'',now(),now())
                """, 9100 + id, id);
            jdbc.update("""
                insert into review_fanout_snapshot (id,tenant_id,project_id,task_id,script_version_id,attempt_no,
                    agent_code,agent_revision,skill_revisions_json,model_id,review_mode,selected_dimensions_json,
                    version_hash,scope_hash,dimensions_hash,unit_set_hash,status,total_units,max_concurrency,created_at,updated_at)
                values (?,9001,9005,?,9006,1,'script-review',1,'[]',1,'DEEP','[]',
                    'v','s','d','u','SUCCEEDED',1,1,now(),now())
                """, id, id);
            jdbc.update("""
                insert into review_fanout_unit (id,snapshot_id,unit_no,unit_key,scope_json,start_offset,end_offset,
                    content_fingerprint,status,candidate_saved,created_at,updated_at)
                values (?,?,1,'unit','{}',0,4,'hash','SUCCEEDED',true,now(),now())
                """, id, id);
            jdbc.update("""
                insert into review_unit_result (snapshot_id,unit_id,child_run_id,attempt_no,version_hash,scope_hash,
                    dimensions_hash,content_fingerprint,coverage_json,candidates_json,report_markdown,payload_hash,created_at,updated_at)
                values (?,?,?,1,'v','s','d','hash','{}','[]',?,'hash',now(),now())
                """, id, id, 9100 + id, id == 7 ? "# current fragment" : null);
        }
    }

    private DriverManagerDataSource database(String purpose) {
        String rehearsalUrl = System.getProperty("retirement.mysql." + purpose);
        if (rehearsalUrl != null) return new DriverManagerDataSource(rehearsalUrl, "root", "");
        return new DriverManagerDataSource("jdbc:h2:mem:retire_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void migrate(DriverManagerDataSource source, String target) {
        var config = Flyway.configure().dataSource(source).locations("classpath:db/migration");
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        config.load().migrate();
    }
}
