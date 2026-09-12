package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptWorkflowPromptBackfillTest {
    @Autowired private ScriptWorkflowService service;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void backfillOnlyFillsEmptyPromptsAndSkipsAllPopulatedTargets() throws Exception {
        long tenantId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000) + 7_000_000;
        long projectId = tenantId + 1;
        jdbc.update("""
            insert into project (id, tenant_id, name, code, owner_id, status, created_by, created_at, updated_at)
            values (?, ?, 'Prompt Backfill', ?, ?, 'ACTIVE', ?, now(), now())
            """, projectId, tenantId, "BACKFILL_" + tenantId, tenantId + 2, tenantId + 2);
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, name, normalized_name, role_type, status, source, prompt, created_by, created_at, updated_at)
            values (?, ?, '已建立角色', '已建立角色', 'LEAD', 'CONFIRMED', 'USER', '人工提示词', ?, now(), now())
            """, tenantId, projectId, tenantId + 2);
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, name, normalized_name, role_type, status, source, prompt, created_by, created_at, updated_at)
            values (?, ?, '缺失角色', '缺失角色', 'LEAD', 'CONFIRMED', 'USER', null, ?, now(), now())
            """, tenantId, projectId, tenantId + 2);
        var ids = jdbc.queryForList("select id from character_asset where project_id = ? order by id", Long.class, projectId);

        invokeApply(tenantId, projectId, "CHARACTER", """
            {"characters":[
              {"id":%d,"prompt":"不应覆盖"},
              {"id":%d,"prompt":"模型填充提示词"}
            ]}
            """.formatted(ids.get(0), ids.get(1)));

        assertThat(jdbc.queryForObject("select prompt from character_asset where id = ?", String.class, ids.get(0)))
            .isEqualTo("人工提示词");
        assertThat(jdbc.queryForObject("select prompt from character_asset where id = ?", String.class, ids.get(1)))
            .isEqualTo("模型填充提示词");
        Object target = invokeTarget(tenantId, projectId, "CHARACTER");
        Method empty = target.getClass().getDeclaredMethod("empty");
        empty.setAccessible(true);
        assertThat(empty.invoke(target)).isEqualTo(true);
    }

    private void invokeApply(long tenantId, long projectId, String type, String content) throws Exception {
        Method method = ScriptWorkflowService.class.getDeclaredMethod(
            "applyGeneratedPrompts", Long.class, Long.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(targetService(), tenantId, projectId, type, content);
    }

    private Object invokeTarget(long tenantId, long projectId, String type) throws Exception {
        Method method = ScriptWorkflowService.class.getDeclaredMethod(
            "promptBackfillTarget", Long.class, Long.class, String.class);
        method.setAccessible(true);
        return method.invoke(targetService(), tenantId, projectId, type);
    }

    private ScriptWorkflowService targetService() throws Exception {
        return AopTestUtils.getTargetObject(service);
    }
}
