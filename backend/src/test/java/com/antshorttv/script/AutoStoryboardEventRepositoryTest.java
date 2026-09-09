package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AutoStoryboardEventRepositoryTest {
    private JdbcTemplate jdbc;
    private AutoStoryboardEventRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:auto_storyboard_event;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop table if exists episode_auto_storyboard_event");
        jdbc.execute("""
            create table episode_auto_storyboard_event (
              id bigint auto_increment primary key, tenant_id bigint not null, project_id bigint not null,
              script_id bigint not null, episode_id bigint not null, episode_key varchar(128) not null,
              source_fingerprint varchar(128) not null, asset_analysis_id bigint not null,
              context_snapshot_id bigint null, policy_version varchar(32) not null,
              status varchar(32) not null, attempt_no int not null default 0,
              execution_id bigint null, error_code varchar(128) null, error_message varchar(1000) null,
              created_by bigint not null, created_at timestamp not null, updated_at timestamp not null,
              next_attempt_at timestamp null, finished_at timestamp null,
              unique (tenant_id, script_id, episode_id, source_fingerprint, policy_version)
            )
            """);
        repository = new AutoStoryboardEventRepository(jdbc);
    }

    @Test
    void recordsOnceClaimsRecoverablyAndKeepsRecognitionIndependentFromDispatchFailure() {
        var draft = new AutoStoryboardEventRepository.Draft(
            7L, 8L, 9L, 10L, "episode-1", "fp", 20L, 30L, 40L);

        long first = repository.recordPending(draft);
        long duplicate = repository.recordPending(draft);
        var claimed = repository.claimNext().orElseThrow();
        repository.markFailed(claimed.id(), "TIMEOUT", "provider timeout", true);

        assertThat(duplicate).isEqualTo(first);
        assertThat(claimed.attemptNo()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select status from episode_auto_storyboard_event where id=?", String.class, first))
            .isEqualTo("RETRYABLE");
    }

    @Test
    void recoversAConsumerCrashAfterClaimBeforeDispatchOutcome() {
        var draft = new AutoStoryboardEventRepository.Draft(
            7L, 8L, 9L, 10L, "episode-1", "fp-crash", 20L, 30L, 40L);
        long id = repository.recordPending(draft);
        repository.claimNext().orElseThrow();
        jdbc.update("update episode_auto_storyboard_event set updated_at=timestampadd(second,-180,now()) where id=?", id);

        assertThat(repository.recoverStaleDispatching(120)).isEqualTo(1);
        assertThat(repository.claimNext().orElseThrow().id()).isEqualTo(id);
    }
}
