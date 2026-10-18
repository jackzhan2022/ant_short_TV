package db.migration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class UnifyStoryboardSkillsMigrationTest {
    private static final String CODE = "short-drama-storyboard-production";
    private static final String PLAN = "short-drama-storyboard-planning";
    private static final String VIDEO = "short-drama-seedance-video-prompt";

    @Test
    void archivesAndMergesTenantsInOriginalOrderAndPreservesOtherAssociations() throws Exception {
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            insert(jdbc, 1, PLAN, "规划规则原文", 10, 11, "2026-01-01 00:00:00", "2026-01-03 00:00:00");
            insert(jdbc, 1, VIDEO, "视频表达原文", 20, 21, "2026-01-02 00:00:00", "2026-01-04 00:00:00");
            insert(jdbc, 2, VIDEO, "另一团队规则", 30, 31, "2026-01-02 00:00:00", "2026-01-05 00:00:00");
            jdbc.update("insert into ai_workflow_agent values (1,'short-drama-storyboard',4,now(),42,'ENABLED',14)");
            jdbc.update("insert into ai_workflow_agent values (2,'short-drama-episode-summary',2,now(),43,'ENABLED',4)");
            List<String> codes = List.of("short-drama-analysis-foundation", PLAN, "short-drama-storyboard-material-reference", VIDEO, "custom-camera-guide");
            for (int index = 0; index < codes.size(); index++) {
                jdbc.update("insert into ai_workflow_agent_skill values (1,?,?,now())", codes.get(index), index);
            }
            jdbc.update("insert into ai_workflow_agent_skill values (2,'short-drama-analysis-foundation',0,now())");
            migrate(connection);
            assertThat(jdbc.queryForList("select skill_code from ai_workflow_agent_skill where agent_id=1 order by load_order", String.class))
                .containsExactly(CODE, "custom-camera-guide");
            assertThat(jdbc.queryForList("select skill_code from ai_workflow_agent_skill where agent_id=2", String.class))
                .containsExactly("short-drama-analysis-foundation");
            assertThat(jdbc.queryForObject("select model_id from ai_workflow_agent where id=1", Long.class)).isEqualTo(42L);
            assertThat(jdbc.queryForObject("select max_steps from ai_workflow_agent where id=1", Integer.class)).isEqualTo(14);
            assertThat(jdbc.queryForObject("select count(*) from tenant_workflow_skill", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from tenant_workflow_skill_merge_archive", Integer.class)).isEqualTo(3);
            String merged = jdbc.queryForObject("select content from tenant_workflow_skill where tenant_id=1 and skill_code=?", String.class, CODE);
            assertThat(merged).contains("规划规则原文", "视频表达原文");
            assertThat(merged.indexOf("规划规则原文")).isLessThan(merged.indexOf("视频表达原文"));
            assertThat(jdbc.queryForObject("select content from tenant_workflow_skill_merge_archive where tenant_id=1 and skill_code=?", String.class, PLAN))
                .isEqualTo(document(PLAN, "规划规则原文"));
            assertThat(jdbc.queryForObject("select created_by from tenant_workflow_skill where tenant_id=1", Long.class)).isEqualTo(10L);
            assertThat(jdbc.queryForObject("select updated_by from tenant_workflow_skill where tenant_id=1", Long.class)).isEqualTo(21L);
            String revision = jdbc.queryForObject("select revision from tenant_workflow_skill where tenant_id=1", String.class);
            migrate(connection);
            assertThat(jdbc.queryForObject("select revision from tenant_workflow_skill where tenant_id=1", String.class)).isEqualTo(revision);
            assertThat(jdbc.queryForObject("select content from tenant_workflow_skill where tenant_id=1", String.class)).isEqualTo(merged);
            assertThat(jdbc.queryForObject("select count(*) from tenant_workflow_skill_merge_archive", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select revision from ai_workflow_agent where id=1", Long.class)).isEqualTo(5L);
        }
    }

    @Test
    void retainsAnExistingUnifiedDocumentAlongsideLegacyRules() throws Exception {
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            insert(jdbc, 1, PLAN, "旧规划", 1, 1, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            insert(jdbc, 1, CODE, "已存在的统一规则", 2, 2, "2026-01-02 00:00:00", "2026-01-05 00:00:00");
            migrate(connection);
            String result = jdbc.queryForObject("select content from tenant_workflow_skill where tenant_id=1", String.class);
            assertThat(result).contains("旧规划", "已存在的统一规则", "name: " + CODE);
            assertThat(result.indexOf("旧规划")).isLessThan(result.indexOf("已存在的统一规则"));
            assertThat(jdbc.queryForObject("select count(*) from tenant_workflow_skill_merge_archive", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void rollsBackEarlierTenantChangesWhenArchivingALaterTenantFails() throws Exception {
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            migrate(connection);
            jdbc.execute("alter table tenant_workflow_skill_merge_archive add constraint reject_tenant check (tenant_id<>99)");
            insert(jdbc, 1, PLAN, "必须保留", 1, 1, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            insert(jdbc, 99, VIDEO, "失败来源", 2, 2, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            assertThatThrownBy(() -> migrate(connection)).isInstanceOf(Exception.class);
            assertThat(jdbc.queryForObject("select content from tenant_workflow_skill where tenant_id=1", String.class))
                .isEqualTo(document(PLAN, "必须保留"));
            assertThat(jdbc.queryForObject("select count(*) from tenant_workflow_skill where skill_code=?", Integer.class, CODE)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from tenant_workflow_skill_merge_archive", Integer.class)).isZero();
            assertThat(connection.getAutoCommit()).isTrue();
        }
    }

    private void migrate(Connection connection) throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        new V135__unify_storyboard_skills().migrate(context);
    }

    private Connection database() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        JdbcTemplate jdbc = jdbc(connection);
        jdbc.execute("""
            create table tenant_workflow_skill (tenant_id bigint,skill_code varchar(128),name varchar(200),description varchar(2000),
            content longtext,revision varchar(64),created_by bigint,updated_by bigint,created_at datetime,updated_at datetime,
            primary key(tenant_id,skill_code))
            """);
        jdbc.execute("create table ai_workflow_agent(id bigint primary key,code varchar(128),revision bigint,updated_at datetime,model_id bigint,status varchar(32),max_steps int)");
        jdbc.execute("create table ai_workflow_agent_skill(agent_id bigint,skill_code varchar(128),load_order int,created_at datetime,primary key(agent_id,skill_code),unique(agent_id,load_order))");
        return connection;
    }

    private JdbcTemplate jdbc(Connection connection) { return new JdbcTemplate(new SingleConnectionDataSource(connection, true)); }

    private String document(String code, String body) { return "---\r\nname: " + code + "\r\ndescription: team\r\n---\r\n\r\n" + body + "\r\n"; }

    private void insert(JdbcTemplate jdbc, long tenant, String code, String body, long creator, long editor, String created, String updated) {
        jdbc.update("insert into tenant_workflow_skill values (?,?,?,?,?,?,?,?,?,?)", tenant, code, code, "team",
            document(code, body), "revision-" + code, creator, editor, Timestamp.valueOf(created), Timestamp.valueOf(updated));
    }
}
