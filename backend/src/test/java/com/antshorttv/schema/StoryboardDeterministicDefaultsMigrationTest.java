package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StoryboardDeterministicDefaultsMigrationTest {
    private static final String V2_PROMPT = "服务端已准备完整可信上下文。严格按 Skill 用 schemaVersion 2 完成整集分镜；sourceFrom 和 sourceTo 必须放在每个分镜对象内部，根对象禁止出现。soundSegmentIds 只能引用 type 为 DIALOGUE、NARRATION 或 INNER_OS 的来源 ID，禁止引用 ACTION 或 METADATA。只调用 save_episode_storyboards，并以保存成功作为终止动作。";

    @Test
    void upgradesOnlyUntouchedBuiltinStoryboardPrompt() {
        JdbcTemplate untouched = prepare("storyboard_v3_untouched", V2_PROMPT, null);
        migrate(untouched.getDataSource(), null);
        assertThat(prompt(untouched)).contains("schemaVersion 3", "sourceAnchor", "后端派生");

        JdbcTemplate customized = prepare("storyboard_v3_customized", "管理员自定义分镜提示", 42L);
        migrate(customized.getDataSource(), null);
        assertThat(prompt(customized)).isEqualTo("管理员自定义分镜提示");
    }

    private JdbcTemplate prepare(String database, String prompt, Long updatedBy) {
        DataSource source = new DriverManagerDataSource(
            "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", "");
        migrate(source, "89");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Long modelId = jdbc.queryForObject("select min(id) from ai_model", Long.class);
        jdbc.update("""
            insert into ai_workflow_agent
              (code, name, description, system_prompt, model_id, temperature, max_tokens,
               max_steps, status, revision, created_by, updated_by, created_at, updated_at)
            values ('short-drama-storyboard', 'Storyboard', '', ?, ?, 0.3, 16384,
                    14, 'ENABLED', 2, null, ?, now(), now())
            """, prompt, modelId, updatedBy);
        return jdbc;
    }

    private String prompt(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
            select system_prompt from ai_workflow_agent where code = 'short-drama-storyboard'
            """, String.class);
    }

    private void migrate(DataSource source, String target) {
        var configuration = Flyway.configure().dataSource(source)
            .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        configuration.load().migrate();
    }
}
