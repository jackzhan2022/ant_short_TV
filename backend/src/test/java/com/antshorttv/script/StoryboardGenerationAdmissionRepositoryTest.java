package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StoryboardGenerationAdmissionRepositoryTest {
    @Test
    void automaticAndManualRequestsShareTheSameSourceAdmission() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:storyboard_admission;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("drop table if exists storyboard_generation_admission");
        jdbc.execute("drop table if exists script_episode");
        jdbc.execute("create table script_episode(id bigint primary key, tenant_id bigint, project_id bigint, content_fingerprint varchar(128), status varchar(32), retired_at timestamp)");
        jdbc.execute("create table storyboard_generation_admission(tenant_id bigint, project_id bigint, episode_id bigint, source_fingerprint varchar(128), execution_id bigint, origin varchar(32), created_at timestamp, updated_at timestamp, primary key(tenant_id,project_id,episode_id,source_fingerprint))");
        jdbc.update("insert into script_episode values(10,7,8,'fp','ACTIVE',null)");
        StoryboardGenerationAdmissionRepository repository =
            new StoryboardGenerationAdmissionRepository(jdbc);

        var automatic = repository.admit(7L, 8L, 10L, "AUTO");
        repository.attach(7L, 8L, 10L, automatic.sourceFingerprint(), 99L);
        var manual = repository.admit(7L, 8L, 10L, "MANUAL");

        assertThat(automatic.created()).isTrue();
        assertThat(manual.created()).isFalse();
        assertThat(manual.executionId()).isEqualTo(99L);
        assertThat(jdbc.queryForObject(
            "select count(*) from storyboard_generation_admission", Integer.class)).isOne();
    }
}
