package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptSplitSnapshotStoreTest {
    @Autowired private ScriptSplitSnapshotStore store;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private ScriptSplitSnapshotStore.SplitScope scope;

    @BeforeEach
    void setUp() {
        jdbc.update("insert into tenant (code, name, type, status, created_at, updated_at) "
            + "values ('split-store', 'Split Store', 'TEAM', 'ACTIVE', now(), now())");
        long tenantId = jdbc.queryForObject(
            "select id from tenant where code = 'split-store'", Long.class);
        jdbc.update("insert into project (tenant_id, name, code, owner_id, status, created_by, created_at, updated_at) "
            + "values (?, 'Split', 'split-store-project', 71, 'ACTIVE', 71, now(), now())", tenantId);
        long projectId = jdbc.queryForObject(
            "select id from project where code = 'split-store-project'", Long.class);
        jdbc.update("insert into script (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at) "
            + "values (?, ?, 'Script', 'MANUAL_EDIT', 'source', 'ACTIVE', 71, now(), now())",
            tenantId, projectId);
        long scriptId = jdbc.queryForObject(
            "select id from script where project_id = ?", Long.class, projectId);
        long modelId = jdbc.queryForObject("select min(id) from ai_model", Long.class);
        jdbc.update("""
            insert into ai_workflow_agent_run
              (agent_code, run_type, tenant_id, user_id, project_id, status, model_id,
               temperature, max_tokens, max_steps, prompt_snapshot, started_at, created_at)
            values ('short-drama-episode-splitting', 'PROJECT', ?, 71, ?, 'RUNNING', ?,
                    0.2, 16384, 8, 'prompt', now(), now())
            """, tenantId, projectId, modelId);
        long runId = jdbc.queryForObject("select max(id) from ai_workflow_agent_run", Long.class);
        scope = new ScriptSplitSnapshotStore.SplitScope(tenantId, projectId, scriptId, runId);
    }

    @Test
    void chunkProgressMutationsAreSerializedToAvoidParentRefreshDeadlocks() throws Exception {
        assertThat(Modifier.isSynchronized(ScriptSplitSnapshotStore.class
            .getMethod("markChunkSucceeded", long.class, int.class, Long.class,
                com.fasterxml.jackson.databind.JsonNode.class).getModifiers())).isTrue();
        assertThat(Modifier.isSynchronized(ScriptSplitSnapshotStore.class
            .getMethod("markChunkFailed", long.class, int.class, String.class, String.class)
            .getModifiers())).isTrue();
    }

    @Test
    void resumesMatchingSnapshotAndRetriesOnlyNonSuccessfulChunks() throws Exception {
        List<ScriptSplitSnapshotStore.SplitChunkSeed> chunks = List.of(
            new ScriptSplitSnapshotStore.SplitChunkSeed(1, 0, 100, 0, 110, "chunk-a"),
            new ScriptSplitSnapshotStore.SplitChunkSeed(2, 100, 200, 90, 200, "chunk-b"));
        long snapshotId = store.createOrResume(
            scope, "hash-a", "OUTPUT_TRUNCATED", "planner-v1", chunks);

        assertThat(store.createOrResume(
            scope, "hash-a", "OUTPUT_TRUNCATED", "planner-v1", chunks)).isEqualTo(snapshotId);
        store.markChunkSucceeded(snapshotId, 1, null, json.readTree("[{\"offset\":90}]"));
        store.markChunkFailed(snapshotId, 2, "MODEL_ERROR", "temporary");

        assertThat(store.retryableChunks(snapshotId))
            .extracting(ScriptSplitSnapshotStore.SplitChunk::chunkNo).containsExactly(2);
        ScriptSplitSnapshotStore.SplitSnapshot snapshot = store.require(snapshotId);
        assertThat(snapshot.completed()).isEqualTo(1);
        assertThat(snapshot.failed()).isEqualTo(1);

        store.markStaleForDifferentHash(scope, "hash-b");
        assertThat(store.require(snapshotId).status()).isEqualTo("STALE");
    }

    @Test
    void cancellationStopsPendingChunksAndSuccessfulProgressNeverRegresses() throws Exception {
        List<ScriptSplitSnapshotStore.SplitChunkSeed> chunks = List.of(
            new ScriptSplitSnapshotStore.SplitChunkSeed(1, 0, 10, 0, 12, "a"),
            new ScriptSplitSnapshotStore.SplitChunkSeed(2, 10, 20, 8, 20, "b"));
        long snapshotId = store.createOrResume(scope, "hash-c", "CONTEXT_PREFLIGHT", "planner-v2", chunks);
        store.markChunkSucceeded(snapshotId, 1, null, json.createArrayNode());
        store.markChunkFailed(snapshotId, 1, "LATE_FAILURE", "must be ignored");
        assertThat(store.require(snapshotId).completed()).isEqualTo(1);

        store.cancel(snapshotId);

        assertThat(store.require(snapshotId).status()).isEqualTo("CANCELLED");
        assertThat(store.retryableChunks(snapshotId)).isEmpty();
    }
}
