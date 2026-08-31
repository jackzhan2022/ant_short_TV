package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RemainingAnalysisBackfillMigrationTest {
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("insert into tenant (id, code, name, type, status, created_at, updated_at) values (9601, 'backfill', 'Backfill', 'TEAM', 'ACTIVE', now(), now())");
        jdbc.update("insert into project (id, tenant_id, name, code, owner_id, status, created_by, created_at, updated_at) values (9602, 9601, 'Single', 'single', 1, 'ACTIVE', 1, now(), now()), (9603, 9601, 'Multi', 'multi', 1, 'ACTIVE', 1, now(), now())");
        jdbc.update("insert into script (id, tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at) values (9604, 9601, 9602, 'One', 'MANUAL_EDIT', '正文', 'ACTIVE', 1, now(), now()), (9605, 9601, 9603, 'Two A', 'MANUAL_EDIT', 'A', 'ACTIVE', 1, now(), now()), (9606, 9601, 9603, 'Two B', 'MANUAL_EDIT', 'B', 'ACTIVE', 1, now(), now())");
        jdbc.update("insert into script_episode (id, tenant_id, project_id, script_id, stable_key, episode_no, title, summary, content, content_fingerprint, reconciliation_status, status, created_at, updated_at) values (9607, 9601, 9602, 9604, 'episode-1', 1, '第一集', '旧概要', '正文', 'hash', 'NEW', 'ACTIVE', now(), now())");
        jdbc.update("insert into character_asset (id, tenant_id, project_id, name, role_type, status, created_by, created_at, updated_at) values (9608, 9601, 9602, ' 林 小满 ', 'LEAD', 'DRAFT', 1, now(), now()), (9609, 9601, 9603, '顾言', 'LEAD', 'DRAFT', 1, now(), now())");
        jdbc.update("insert into scene_asset (id, tenant_id, project_id, name, scene_type, status, created_by, created_at, updated_at) values (9610, 9601, 9602, '旧 仓库', 'INDOOR', 'DRAFT', 1, now(), now())");
        jdbc.update("insert into prop_asset (id, tenant_id, project_id, name, prop_type, status, created_by, created_at, updated_at) values (9611, 9601, 9602, '染血的钥匙', 'KEY_ITEM', 'DRAFT', 1, now(), now())");
    }

    @Test
    void backfillsLegacySummaryAndOnlyUnambiguousScriptOwnership() throws Exception {
        invokeBackfill();

        assertThat(jdbc.queryForObject("select content_json from script_episode_summary where episode_id = 9607", String.class))
            .contains("\"summary\":\"旧概要\"").contains("\"highlights\":[]").contains("\"endingHook\":null");
        assertThat(jdbc.queryForObject("select script_id from character_asset where id = 9608", Long.class)).isEqualTo(9604L);
        assertThat(jdbc.queryForObject("select normalized_name from character_asset where id = 9608", String.class)).isEqualTo("林小满");
        assertThat(jdbc.queryForObject("select source from scene_asset where id = 9610", String.class)).isEqualTo("LEGACY");
        assertThat(jdbc.queryForObject("select script_id from prop_asset where id = 9611", Long.class)).isEqualTo(9604L);
        assertThat(jdbc.queryForObject("select script_id from character_asset where id = 9609", Long.class)).isNull();
    }

    @Test
    void backfillIsIdempotentAndDoesNotReplaceExistingFormalSummary() throws Exception {
        invokeBackfill();
        jdbc.update("update script_episode_summary set content_json = '{\"summary\":\"用户修改\",\"highlights\":[],\"endingHook\":null}', source = 'USER' where episode_id = 9607");
        invokeBackfill();

        assertThat(jdbc.queryForObject("select count(*) from script_episode_summary where episode_id = 9607", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select content_json from script_episode_summary where episode_id = 9607", String.class)).contains("用户修改");
        assertThat(jdbc.queryForObject("select source from script_episode_summary where episode_id = 9607", String.class)).isEqualTo("USER");
    }

    private void invokeBackfill() throws Exception {
        Class<?> migration = Class.forName("db.migration.V76__backfill_formal_script_analysis_data");
        Method method = migration.getMethod("backfill", java.sql.Connection.class);
        java.sql.Connection connection = DataSourceUtils.getConnection(jdbc.getDataSource());
        method.invoke(null, connection);
    }
}
