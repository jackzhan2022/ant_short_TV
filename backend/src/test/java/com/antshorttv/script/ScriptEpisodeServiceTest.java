package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptEpisodeServiceTest {
    @Autowired private ApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void persistsStableIdsAcrossVersionsAndRetiresRemovedRows() throws Exception {
        seedScriptVersions();
        Object service = service();
        Method reconcile = service.getClass().getMethod(
            "reconcileAndPersist", Long.class, Long.class, Long.class, Long.class, List.class);

        reconcile.invoke(service, 8101L, 8102L, 8103L, 8104L, List.of(
            new ScriptEpisodeResponse(1, "第1集", "A剧情"),
            new ScriptEpisodeResponse(2, "第2集", "B剧情")
        ));
        String stableA = jdbc.queryForObject(
            "select stable_key from script_episode where script_id = 8103 and episode_no = 1 and retired_at is null",
            String.class);
        String stableB = jdbc.queryForObject(
            "select stable_key from script_episode where script_id = 8103 and episode_no = 2 and retired_at is null",
            String.class);

        List<?> current = (List<?>) reconcile.invoke(
            service, 8101L, 8102L, 8103L, 8105L, List.of(
                new ScriptEpisodeResponse(1, "第1集", "B剧情"),
                new ScriptEpisodeResponse(2, "第2集", "C剧情")
            ));

        assertThat(current).allSatisfy(item -> assertThat(episodeId(item)).isNotNull());
        assertThat(jdbc.queryForObject(
            "select stable_key from script_episode where script_id = 8103 and episode_no = 1 and retired_at is null",
            String.class)).isEqualTo(stableB);
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode where stable_key = ? and retired_at is not null",
            Integer.class, stableA)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode where script_id = 8103 and retired_at is null",
            Integer.class)).isEqualTo(2);
    }

    private Object service() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.antshorttv.script.ScriptEpisodeService");
        } catch (ClassNotFoundException exception) {
            assertThat(exception).as("ScriptEpisodeService must exist").isNull();
            throw exception;
        }
        return applicationContext.getBean(type);
    }

    private Object episodeId(Object episode) {
        try {
            return episode.getClass().getMethod("episodeId").invoke(episode);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private void seedScriptVersions() {
        jdbc.update("""
            insert into script
              (id, tenant_id, project_id, title, source_type, content, status, current_version_id,
               created_by, created_at, updated_at)
            values (8103, 8101, 8102, '稳定分集', 'MANUAL_EDIT', 'A剧情', 'DRAFT', 8105,
                    8106, now(), now())
            """);
        jdbc.update("""
            insert into script_version
              (id, tenant_id, project_id, script_id, version_no, source_type, content, status, created_by, created_at)
            values
              (8104, 8101, 8102, 8103, 1, 'MANUAL_EDIT', 'A剧情', 'DRAFT', 8106, now()),
              (8105, 8101, 8102, 8103, 2, 'MANUAL_EDIT', 'B剧情', 'DRAFT', 8106, now())
            """);
    }
}
