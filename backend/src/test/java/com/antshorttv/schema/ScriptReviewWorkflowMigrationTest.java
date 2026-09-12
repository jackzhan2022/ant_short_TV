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
                 'child_run_id', 'attempt_no', 'report_saved', 'error_code',
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
                 'content_fingerprint', 'report_markdown',
                 'payload_hash'
               )
            """, Integer.class)).isEqualTo(10);

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
    void createsAttemptUnitReportStatusAndLookupIndexes() {
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
    void keepsMarkdownReportStorageAndRemovesRetiredIssueTables() {
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.tables
             where lower(table_name) in ('review_issue','review_issue_hit','review_issue_event','review_batch_repair')
            """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.columns
             where lower(table_name)='review_unit_result' and lower(column_name)='report_markdown'
            """, Integer.class)).isOne();
    }
}
