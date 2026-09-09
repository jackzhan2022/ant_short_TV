package com.antshorttv.script;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EpisodePromptContextRepository {
    private final JdbcTemplate jdbc;

    public EpisodePromptContextRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot createOrLoad(Draft draft) {
        try {
            jdbc.update("""
                insert into episode_prompt_context_snapshot
                  (tenant_id, project_id, script_id, episode_id, model_id, source_fingerprint,
                   global_understanding_hash, context_hash, rules_revision, tool_protocol_revision,
                   common_prefix, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, draft.tenantId(), draft.projectId(), draft.scriptId(), draft.episodeId(),
                draft.modelId(), draft.sourceFingerprint(), draft.globalUnderstandingHash(),
                draft.contextHash(), draft.rulesRevision(), draft.toolProtocolRevision(),
                draft.commonPrefix());
        } catch (DuplicateKeyException ignored) {
            // Immutable content identity already exists; load it below.
        }
        return jdbc.queryForObject("""
            select id, tenant_id, project_id, script_id, episode_id, model_id,
                   source_fingerprint, global_understanding_hash, context_hash,
                   rules_revision, tool_protocol_revision, common_prefix
              from episode_prompt_context_snapshot
             where tenant_id = ? and model_id = ? and context_hash = ?
            """, (row, index) -> new Snapshot(
                row.getLong("id"), row.getLong("tenant_id"), row.getLong("project_id"),
                row.getLong("script_id"), row.getLong("episode_id"), row.getLong("model_id"),
                row.getString("source_fingerprint"), row.getString("global_understanding_hash"),
                row.getString("context_hash"), row.getString("rules_revision"),
                row.getString("tool_protocol_revision"), row.getString("common_prefix")),
            draft.tenantId(), draft.modelId(), draft.contextHash());
    }

    public Optional<Snapshot> findLatest(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long episodeId,
        Long modelId,
        String sourceFingerprint
    ) {
        return jdbc.query("""
            select id, tenant_id, project_id, script_id, episode_id, model_id,
                   source_fingerprint, global_understanding_hash, context_hash,
                   rules_revision, tool_protocol_revision, common_prefix
              from episode_prompt_context_snapshot
             where tenant_id = ? and project_id = ? and script_id = ? and episode_id = ?
               and model_id = ? and source_fingerprint = ?
             order by id desc limit 1
            """, (row, index) -> new Snapshot(
                row.getLong("id"), row.getLong("tenant_id"), row.getLong("project_id"),
                row.getLong("script_id"), row.getLong("episode_id"), row.getLong("model_id"),
                row.getString("source_fingerprint"), row.getString("global_understanding_hash"),
                row.getString("context_hash"), row.getString("rules_revision"),
                row.getString("tool_protocol_revision"), row.getString("common_prefix")),
            tenantId, projectId, scriptId, episodeId, modelId, sourceFingerprint)
            .stream().findFirst();
    }

    public record Draft(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long episodeId,
        Long modelId,
        String sourceFingerprint,
        String globalUnderstandingHash,
        String contextHash,
        String rulesRevision,
        String toolProtocolRevision,
        String commonPrefix
    ) {}

    public record Snapshot(
        Long id,
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long episodeId,
        Long modelId,
        String sourceFingerprint,
        String globalUnderstandingHash,
        String contextHash,
        String rulesRevision,
        String toolProtocolRevision,
        String commonPrefix
    ) {}
}
