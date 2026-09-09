package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EpisodePromptContextRepositoryTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:episode_prompt_context;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop table if exists episode_prompt_context_snapshot");
        jdbc.execute("""
            create table episode_prompt_context_snapshot (
              id bigint auto_increment primary key,
              tenant_id bigint not null, project_id bigint not null, script_id bigint not null,
              episode_id bigint not null, model_id bigint not null,
              source_fingerprint varchar(64) not null, global_understanding_hash varchar(64) not null,
              context_hash varchar(64) not null, rules_revision varchar(64) not null,
              tool_protocol_revision varchar(64) not null, common_prefix clob not null,
              created_at timestamp not null,
              unique (tenant_id, model_id, context_hash)
            )
            """);
    }

    @Test
    void reloadsTheSameImmutableSnapshotAfterRepositoryRestart() {
        var draft = new EpisodePromptContextRepository.Draft(
            7L, 8L, 9L, 10L, 11L, "source-fp", "global-hash", "context-hash",
            "rules-v1", "tools-v1", "FROZEN PREFIX");
        var first = new EpisodePromptContextRepository(jdbc).createOrLoad(draft);
        var afterRestart = new EpisodePromptContextRepository(jdbc).createOrLoad(draft);

        assertThat(afterRestart.id()).isEqualTo(first.id());
        assertThat(afterRestart.commonPrefix()).isEqualTo("FROZEN PREFIX");
        assertThat(jdbc.queryForObject(
            "select count(*) from episode_prompt_context_snapshot", Integer.class)).isEqualTo(1);
    }
}
