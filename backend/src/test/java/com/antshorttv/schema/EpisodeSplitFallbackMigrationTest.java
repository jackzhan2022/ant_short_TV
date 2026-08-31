package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class EpisodeSplitFallbackMigrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void createsPersistedSplitSnapshotsAndRetryableChunks() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(columns(jdbc, "script_split_snapshot")).contains(
            "tenant_id", "project_id", "script_id", "parent_run_id", "content_hash",
            "mode", "fallback_reason", "status", "planner_version", "total_chunks",
            "completed_chunks", "failed_chunks", "created_at", "finished_at", "updated_at");
        assertThat(columns(jdbc, "script_split_chunk")).contains(
            "snapshot_id", "chunk_no", "core_start", "core_end", "context_start",
            "context_end", "content_hash", "status", "ai_call_log_id", "candidate_json",
            "error_code", "error_message", "created_at", "updated_at");
        assertThat(indexes(jdbc, "script_split_snapshot"))
            .contains("idx_script_split_snapshot_lookup");
        assertThat(indexes(jdbc, "script_split_chunk"))
            .contains("idx_script_split_chunk_retry");
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.table_constraints
             where lower(table_name) = 'script_split_chunk'
               and lower(constraint_name) = 'uk_script_split_chunk_no'
               and constraint_type = 'UNIQUE'
            """, Integer.class)).isEqualTo(1);
    }

    private List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
            select lower(column_name) from information_schema.columns
             where lower(table_name) = ? order by ordinal_position
            """, String.class, table);
    }

    private List<String> indexes(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
            select distinct lower(index_name) from information_schema.indexes
             where lower(table_name) = ?
            """, String.class, table);
    }
}
