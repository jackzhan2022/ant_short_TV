package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptGlobalUnderstandingRepositoryTest {
    @Autowired
    private ScriptGlobalUnderstandingRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private long tenantId;
    private long projectId;
    private long scriptId;

    @BeforeEach
    void setUp() {
        jdbc.update("insert into tenant (code, name, type, status, created_at, updated_at) "
            + "values ('global-test', 'Global Test', 'TEAM', 'ACTIVE', now(), now())");
        tenantId = jdbc.queryForObject("select id from tenant where code = 'global-test'", Long.class);
        jdbc.update("insert into project (tenant_id, name, code, owner_id, status, created_by, created_at, updated_at) "
            + "values (?, 'Project', 'global-project', 91, 'ACTIVE', 91, now(), now())", tenantId);
        projectId = jdbc.queryForObject("select id from project where code = 'global-project'", Long.class);
        jdbc.update("insert into script (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at) "
            + "values (?, ?, 'Script', 'MANUAL_EDIT', 'original', 'ACTIVE', 91, now(), now())",
            tenantId, projectId);
        scriptId = jdbc.queryForObject("select id from script where project_id = ?", Long.class, projectId);
    }

    @Test
    void insertsThenReplacesTheCurrentDocumentForTheSameScript() throws Exception {
        long firstId = repository.upsert(new ScriptGlobalUnderstandingDocument(
            null, tenantId, projectId, scriptId, 1,
            json.readTree("{\"logline\":\"first\"}"), "hash-1", null, 91L, 91L, null, null));
        long secondId = repository.upsert(new ScriptGlobalUnderstandingDocument(
            null, tenantId, projectId, scriptId, 2,
            json.readTree("{\"logline\":\"second\"}"), "hash-2", null, 91L, 92L, null, null));

        assertThat(secondId).isEqualTo(firstId);
        ScriptGlobalUnderstandingDocument current = repository.findCurrent(tenantId, scriptId).orElseThrow();
        assertThat(current.schemaVersion()).isEqualTo(2);
        assertThat(current.content().path("logline").asText()).isEqualTo("second");
        assertThat(current.analyzedContentHash()).isEqualTo("hash-2");
        assertThat(current.updatedBy()).isEqualTo(92L);
    }

    @Test
    void isolatesLookupByTenant() throws Exception {
        repository.upsert(new ScriptGlobalUnderstandingDocument(
            null, tenantId, projectId, scriptId, 1,
            json.readTree("{\"logline\":\"only\"}"), "hash", null, 91L, 91L, null, null));

        assertThat(repository.findCurrent(tenantId + 1, scriptId)).isEmpty();
        assertThat(repository.findCurrent(tenantId, scriptId)).isPresent();
    }
}
