package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ScriptAssetExtractionCoordinationRepositoryTest {
    @Test
    void reusesEquivalentOwnerAndReportsAConflictingOwner() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt int,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);

        var created = repository.admit(7L, 8L, 9L, "fingerprint-a", 101L, 1, 1);
        var reused = repository.admit(7L, 8L, 9L, "fingerprint-a", 102L, 1, 1);
        var conflict = repository.admit(7L, 8L, 9L, "fingerprint-b", 103L, 1, 1);

        assertThat(created.kind()).isEqualTo("ACQUIRED");
        assertThat(reused.kind()).isEqualTo("REUSED");
        assertThat(reused.ownerExecutionId()).isEqualTo(101L);
        assertThat(conflict.kind()).isEqualTo("CONFLICT");
        assertThat(conflict.ownerExecutionId()).isEqualTo(101L);
    }

    @Test
    void attachesOnlyTheClaimedFingerprintToItsExecution() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination_attach;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt int,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);

        repository.admit(7L, 8L, 9L, "fingerprint-a", null, 1, 1);

        assertThat(repository.attach(7L, 8L, 9L, "fingerprint-a", 101L, 1, 1)).isTrue();
        assertThat(repository.attach(7L, 8L, 9L, "fingerprint-b", 102L, 1, 1)).isFalse();
        assertThat(jdbc.queryForObject("select owner_execution_id from script_asset_extraction_coordination",
            Long.class)).isEqualTo(101L);
    }

    @Test
    void onlyTheCurrentOwnerCanReleaseCoordination() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination_release;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt int,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);
        repository.admit(7L, 8L, 9L, "fingerprint-a", 101L, 2, 3);

        assertThat(repository.release(7L, 8L, 9L, 101L, 1, 3)).isFalse();
        assertThat(repository.release(7L, 8L, 9L, 101L, 2, 3)).isTrue();
        assertThat(jdbc.queryForObject("select state from script_asset_extraction_coordination", String.class))
            .isEqualTo("IDLE");
    }

    @Test
    void resumedExecutionTakesOverItsOwnCoordinationWithANewerFence() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination_takeover;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt int,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);
        repository.admit(7L, 8L, 9L, "fingerprint-a", 101L, 1, 1);

        assertThat(repository.admit(7L, 8L, 9L, "fingerprint-a", 101L, 2, 2).kind())
            .isEqualTo("ACQUIRED");
        assertThat(repository.release(7L, 8L, 9L, 101L, 1, 1)).isFalse();
        assertThat(repository.release(7L, 8L, 9L, 101L, 2, 2)).isTrue();
    }

    @Test
    void ownerCanAdvanceTheAdmissionFenceToItsClaimedAttempt() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination_renew;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt int,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);
        repository.admit(7L, 8L, 9L, "fingerprint-a", null, 1, 0);
        assertThat(repository.attach(7L, 8L, 9L, "fingerprint-a", 101L, 1, 0)).isTrue();

        assertThat(repository.renew(7L, 8L, 9L, 101L, 1, 55)).isTrue();
        assertThat(repository.release(7L, 8L, 9L, 101L, 1, 0)).isFalse();
        assertThat(repository.release(7L, 8L, 9L, 101L, 1, 55)).isTrue();
    }

    @Test
    void terminalExecutionRecoveryReleasesOnlyItsCurrentOwner() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination_terminal;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt int,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        jdbc.execute("create table ai_execution_task(id bigint primary key, status varchar(32))");
        jdbc.update("insert into ai_execution_task values (101, 'CANCELED')");
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);
        repository.admit(7L, 8L, 9L, "fingerprint-a", 101L, 1, 1);

        assertThat(repository.recoverTerminalOwners()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select state from script_asset_extraction_coordination", String.class))
            .isEqualTo("IDLE");
    }

    @Test
    void formalWriteRequiresTheCurrentOwnerFence() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:asset_coordination_write_fence;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            create table script_asset_extraction_coordination(
              id bigint auto_increment primary key, tenant_id bigint, project_id bigint, script_id bigint,
              owner_execution_id bigint, owner_execution_version int, owner_attempt bigint,
              source_fingerprint varchar(128), state varchar(32), created_at timestamp, updated_at timestamp,
              released_at timestamp, unique(tenant_id, project_id, script_id))
            """);
        ScriptAssetExtractionCoordinationRepository repository =
            new ScriptAssetExtractionCoordinationRepository(jdbc);
        repository.admit(7L, 8L, 9L, "fingerprint-a", 101L, 2, 55L);

        assertThatThrownBy(() -> repository.requireCurrentOwner(7L, 8L, 9L, 101L, 1, 54L))
            .isInstanceOf(com.antshorttv.execution.AiExecutionClaimLostException.class);
        repository.requireCurrentOwner(7L, 8L, 9L, 101L, 2, 55L);
    }
}
