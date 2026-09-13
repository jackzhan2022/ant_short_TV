package com.antshorttv.script;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

@Repository
class ScriptAssetExtractionCoordinationRepository {
    private final JdbcTemplate jdbc;

    ScriptAssetExtractionCoordinationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Admission admit(Long tenantId, Long projectId, Long scriptId, String fingerprint,
                    Long executionId, int executionVersion, long attempt) {
        try {
            jdbc.update("""
                insert into script_asset_extraction_coordination
                  (tenant_id, project_id, script_id, owner_execution_id, owner_execution_version,
                   owner_attempt, source_fingerprint, state, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'OWNED', now(), now())
                """, tenantId, projectId, scriptId, executionId, executionVersion, attempt, fingerprint);
            return new Admission("ACQUIRED", executionId);
        } catch (DuplicateKeyException ignored) {
            // Lock the single script row before deciding reuse versus conflict.
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select owner_execution_id, owner_execution_version, owner_attempt, source_fingerprint, state
              from script_asset_extraction_coordination
             where tenant_id=? and project_id=? and script_id=? for update
            """, tenantId, projectId, scriptId);
        Map<String, Object> row = rows.get(0);
        Long owner = row.get("owner_execution_id") == null ? null
            : ((Number) row.get("owner_execution_id")).longValue();
        if (owner == null || "IDLE".equals(row.get("state"))) {
            jdbc.update("""
                update script_asset_extraction_coordination
                   set owner_execution_id=?, owner_execution_version=?, owner_attempt=?, source_fingerprint=?,
                       state='OWNED', released_at=null, updated_at=now()
                 where tenant_id=? and project_id=? and script_id=?
                """, executionId, executionVersion, attempt, fingerprint, tenantId, projectId, scriptId);
            return new Admission("ACQUIRED", executionId);
        }
        int ownerVersion = ((Number) row.get("owner_execution_version")).intValue();
        long ownerAttempt = ((Number) row.get("owner_attempt")).longValue();
        if (owner.equals(executionId) && (executionVersion > ownerVersion
            || executionVersion == ownerVersion && attempt > ownerAttempt)) {
            jdbc.update("""
                update script_asset_extraction_coordination
                   set owner_execution_version=?, owner_attempt=?, updated_at=now()
                 where tenant_id=? and project_id=? and script_id=? and state='OWNED'
                   and owner_execution_id=? and owner_execution_version=? and owner_attempt=?
                """, executionVersion, attempt, tenantId, projectId, scriptId,
                executionId, ownerVersion, ownerAttempt);
            return new Admission("ACQUIRED", executionId);
        }
        return new Admission(fingerprint.equals(row.get("source_fingerprint")) ? "REUSED" : "CONFLICT", owner);
    }

    boolean attach(Long tenantId, Long projectId, Long scriptId, String fingerprint,
                   Long executionId, int executionVersion, long attempt) {
        return jdbc.update("""
            update script_asset_extraction_coordination
               set owner_execution_id=?, owner_execution_version=?, owner_attempt=?, updated_at=now()
             where tenant_id=? and project_id=? and script_id=? and source_fingerprint=?
               and state='OWNED' and owner_execution_id is null
            """, executionId, executionVersion, attempt, tenantId, projectId, scriptId, fingerprint) == 1;
    }

    boolean renew(Long tenantId, Long projectId, Long scriptId,
                  Long executionId, int executionVersion, long attempt) {
        return jdbc.update("""
            update script_asset_extraction_coordination
               set owner_execution_version=?, owner_attempt=?, updated_at=now()
             where tenant_id=? and project_id=? and script_id=? and state='OWNED'
               and owner_execution_id=?
               and (owner_execution_version < ?
                 or (owner_execution_version = ? and owner_attempt <= ?))
            """, executionVersion, attempt, tenantId, projectId, scriptId, executionId,
            executionVersion, executionVersion, attempt) == 1;
    }

    boolean release(Long tenantId, Long projectId, Long scriptId,
                    Long executionId, int executionVersion, long attempt) {
        return jdbc.update("""
            update script_asset_extraction_coordination
               set owner_execution_id=null, owner_execution_version=null, owner_attempt=null,
                   state='IDLE', released_at=now(), updated_at=now()
             where tenant_id=? and project_id=? and script_id=? and state='OWNED'
               and owner_execution_id=? and owner_execution_version=? and owner_attempt=?
            """, tenantId, projectId, scriptId, executionId, executionVersion, attempt) == 1;
    }

    void requireCurrentOwner(Long tenantId, Long projectId, Long scriptId,
                             Long executionId, int executionVersion, long attempt) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select owner_execution_id, owner_execution_version, owner_attempt, state
              from script_asset_extraction_coordination
             where tenant_id=? and project_id=? and script_id=? for update
            """, tenantId, projectId, scriptId);
        if (rows.size() != 1) {
            throw new com.antshorttv.execution.AiExecutionClaimLostException(executionId);
        }
        Map<String, Object> row = rows.get(0);
        boolean current = "OWNED".equals(row.get("state"))
            && executionId.equals(((Number) row.get("owner_execution_id")).longValue())
            && executionVersion == ((Number) row.get("owner_execution_version")).intValue()
            && attempt == ((Number) row.get("owner_attempt")).longValue();
        if (!current) {
            throw new com.antshorttv.execution.AiExecutionClaimLostException(executionId);
        }
    }

    @Scheduled(fixedDelayString = "${ai.asset-extraction.coordination-recovery-delay-ms:30000}")
    public void recoverTerminalOwnersScheduled() {
        recoverTerminalOwners();
    }

    int recoverTerminalOwners() {
        List<Owner> owners = jdbc.query("""
            select coordination.tenant_id, coordination.project_id, coordination.script_id,
                   coordination.owner_execution_id, coordination.owner_execution_version,
                   coordination.owner_attempt
              from script_asset_extraction_coordination coordination
              join ai_execution_task execution on execution.id = coordination.owner_execution_id
             where coordination.state = 'OWNED'
               and execution.status in ('SUCCEEDED', 'FAILED', 'CANCELED', 'TIMED_OUT')
            """, (row, index) -> new Owner(
                row.getLong("tenant_id"), row.getLong("project_id"), row.getLong("script_id"),
                row.getLong("owner_execution_id"), row.getInt("owner_execution_version"),
                row.getLong("owner_attempt")));
        return (int) owners.stream().filter(owner -> release(owner.tenantId(), owner.projectId(), owner.scriptId(),
            owner.executionId(), owner.executionVersion(), owner.attempt())).count();
    }

    record Admission(String kind, Long ownerExecutionId) {}

    private record Owner(
        Long tenantId, Long projectId, Long scriptId, Long executionId, int executionVersion, long attempt
    ) {}
}
