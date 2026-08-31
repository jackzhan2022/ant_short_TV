package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptReviewWorkflowMigrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsReviewFanoutPersistenceAndFrozenWorkflowReferences() {
        assertThat(jdbc.queryForObject("""
            select count(distinct lower(table_name))
              from information_schema.tables
             where lower(table_name) in (
               'review_fanout_snapshot', 'review_fanout_unit', 'review_unit_result'
             )
            """, Integer.class)).isEqualTo(3);

        assertThat(jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'review_fanout_snapshot'
               and lower(column_name) in (
                 'task_id', 'script_version_id', 'attempt_no', 'agent_code',
                 'agent_revision', 'skill_revisions_json', 'model_id', 'review_mode',
                 'version_hash', 'scope_hash', 'dimensions_hash', 'unit_set_hash',
                 'status', 'total_units', 'completed_units', 'failed_units',
                 'current_unit_id', 'aggregation_run_id', 'aggregation_status',
                 'max_concurrency'
               )
            """, Integer.class)).isEqualTo(20);

        assertThat(jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'review_fanout_unit'
               and lower(column_name) in (
                 'snapshot_id', 'unit_no', 'unit_key', 'scope_json',
                 'start_offset', 'end_offset', 'content_fingerprint', 'status',
                 'child_run_id', 'attempt_no', 'candidate_saved', 'error_code',
                 'error_message'
               )
            """, Integer.class)).isEqualTo(13);

        assertThat(jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'review_unit_result'
               and lower(column_name) in (
                 'snapshot_id', 'unit_id', 'child_run_id', 'attempt_no',
                 'version_hash', 'scope_hash', 'dimensions_hash',
                 'content_fingerprint', 'coverage_json', 'candidates_json',
                 'payload_hash'
               )
            """, Integer.class)).isEqualTo(11);

        assertThat(jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'review_task'
               and lower(column_name) in (
                 'workflow_agent_code', 'workflow_agent_revision',
                 'workflow_agent_run_id', 'workflow_phase', 'workflow_attempt_no',
                 'version_hash', 'scope_hash', 'dimensions_hash',
                 'fanout_snapshot_id', 'aggregation_run_id', 'retry_kind', 'stale'
               )
            """, Integer.class)).isEqualTo(12);
    }

    @Test
    void createsAttemptUnitCandidateStatusAndLookupIndexes() {
        assertThat(jdbc.queryForObject("""
            select count(distinct lower(index_name))
              from information_schema.indexes
             where lower(index_name) in (
               'idx_review_fanout_snapshot_status',
               'idx_review_fanout_snapshot_hashes',
               'idx_review_fanout_unit_status',
               'idx_review_unit_result_hashes',
               'idx_review_task_workflow_run',
               'idx_review_task_fanout_snapshot'
             )
            """, Integer.class)).isEqualTo(6);

        assertThat(jdbc.queryForObject("""
            select count(distinct lower(constraint_name))
              from information_schema.table_constraints
             where constraint_type = 'UNIQUE'
               and lower(constraint_name) in (
                 'uk_review_fanout_snapshot_attempt',
                 'uk_review_fanout_unit_key',
                 'uk_review_unit_result_current'
               )
            """, Integer.class)).isEqualTo(3);
    }

    @Test
    void keepsHistoricalFormalReviewRowsReadableWithoutCandidateBackfill() {
        jdbc.update("""
            insert into review_project
              (id, tenant_id, name, source_type, original_content, status, created_by, created_at, updated_at)
            values (98401, 98400, '历史审核', 'TXT', '第一场\n人物：台词', 'ACTIVE', 1, now(), now())
            """);
        jdbc.update("""
            insert into review_script_version
              (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at)
            values (98402, 98400, 98401, 1, 'TXT', '第一场\n人物：台词', 1, now(), now())
            """);
        jdbc.update("""
            insert into review_task
              (id, tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type, status, overall_progress,
               idempotency_key, created_by, created_at, updated_at)
            values (98403, 98400, 98401, 98402, 1, 'QUICK', '["台词合理性"]',
                    'ALL', 'COMPLETED', 100, 'legacy-review-98403', 1, now(), now())
            """);
        jdbc.update("""
            insert into review_issue
              (id, tenant_id, project_id, task_id, script_version_id, round_no, issue_no,
               dimension, severity, title, excerpt, problem, suggestion, status,
               created_at, updated_at)
            values (98404, 98400, 98401, 98403, 98402, 1, 'R1-001', '台词合理性',
                    'MEDIUM', '台词缺少上下文', '人物：台词', '表达不清', '补充对象', 'new', now(), now())
            """);
        jdbc.update("""
            insert into review_issue_hit
              (id, tenant_id, project_id, task_id, issue_id, hit_no, line_no, excerpt,
               selected, created_at, updated_at)
            values (98405, 98400, 98401, 98403, 98404, 1, 2, '人物：台词', true, now(), now())
            """);
        jdbc.update("""
            insert into review_issue_event
              (id, tenant_id, project_id, task_id, issue_id, event_type, new_status,
               created_by, created_at)
            values (98406, 98400, 98401, 98403, 98404, 'CREATED', 'new', 1, now())
            """);
        jdbc.update("""
            insert into review_batch_repair
              (id, tenant_id, project_id, task_id, issue_id, action_type, status,
               created_by, created_at, updated_at)
            values (98407, 98400, 98401, 98403, 98404, 'REPLACE', 'PENDING', 1, now(), now())
            """);
        jdbc.update("""
            insert into review_export_record
              (id, tenant_id, project_id, version_id, task_id, export_type, export_status,
               created_at, updated_at)
            values (98408, 98400, 98401, 98402, 98403, 'JSON', 'COMPLETED', now(), now())
            """);

        assertThat(jdbc.queryForObject("select count(*) from review_project where id = 98401", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_script_version where id = 98402", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_task where id = 98403", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue where id = 98404", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue_hit where id = 98405", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue_event where id = 98406", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_batch_repair where id = 98407", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_export_record where id = 98408", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_unit_result", Integer.class)).isZero();
    }
}
