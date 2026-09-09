package com.antshorttv.script;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class StoryboardGenerationAdmissionRepository {
    private final JdbcTemplate jdbc;

    StoryboardGenerationAdmissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Admission admit(Long tenantId, Long projectId, Long episodeId, String origin) {
        Map<String, Object> episode = jdbc.queryForMap("""
            select content_fingerprint from script_episode
             where id=? and tenant_id=? and project_id=? and status='ACTIVE'
               and retired_at is null for update
            """, episodeId, tenantId, projectId);
        String fingerprint = String.valueOf(episode.get("content_fingerprint"));
        boolean created = false;
        try {
            jdbc.update("""
                insert into storyboard_generation_admission
                  (tenant_id, project_id, episode_id, source_fingerprint, origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())
                """, tenantId, projectId, episodeId, fingerprint, origin);
            created = true;
        } catch (DuplicateKeyException ignored) {
            // The unique source row is shared by automatic and manual requests.
        }
        List<Long> executions = jdbc.query("""
            select execution_id from storyboard_generation_admission
             where tenant_id=? and project_id=? and episode_id=? and source_fingerprint=? for update
            """, (row, index) -> {
                long value = row.getLong(1);
                return row.wasNull() ? null : value;
            }, tenantId, projectId, episodeId, fingerprint);
        return new Admission(fingerprint, created, executions.isEmpty() ? null : executions.get(0));
    }

    void attach(Long tenantId, Long projectId, Long episodeId, String fingerprint, Long executionId) {
        jdbc.update("""
            update storyboard_generation_admission set execution_id=?, updated_at=now()
             where tenant_id=? and project_id=? and episode_id=? and source_fingerprint=?
            """, executionId, tenantId, projectId, episodeId, fingerprint);
    }

    void reopen(Long tenantId, Long projectId, Long episodeId, String fingerprint, Long executionId, String origin) {
        jdbc.update("""
            update storyboard_generation_admission
               set execution_id=null, origin=?, updated_at=now()
             where tenant_id=? and project_id=? and episode_id=? and source_fingerprint=?
               and execution_id=?
            """, origin, tenantId, projectId, episodeId, fingerprint, executionId);
    }

    record Admission(String sourceFingerprint, boolean created, Long executionId) {}
}
