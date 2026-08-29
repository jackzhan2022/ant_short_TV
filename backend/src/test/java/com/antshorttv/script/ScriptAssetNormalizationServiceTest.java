package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScriptAssetNormalizationServiceTest {
    @Autowired private ApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void persistsStringCandidatesIdempotentlyWithoutWritingCanonicalAssets() throws Exception {
        Object service = service();
        Method normalize = method(service);
        Object first = normalize.invoke(service,
            9101L, 9102L, 9103L, 9104L, 9105L, 9106L, 9107L, 9108L, 9109L,
            "recognition-attempt-1",
            "{\"characters\":[\"林夏\"],\"scenes\":[\"天台\"],\"props\":[\"录音笔\"]}");
        Object replay = normalize.invoke(service,
            9101L, 9102L, 9103L, 9104L, 9105L, 9106L, 9107L, 9108L, 9109L,
            "recognition-attempt-1",
            "{\"characters\":[\"林夏\"],\"scenes\":[\"天台\"],\"props\":[\"录音笔\"]}");
        JsonNode result = objectMapper.valueToTree(first);
        JsonNode replayResult = objectMapper.valueToTree(replay);

        assertThat(result.path("status").asText()).isEqualTo("READY_FOR_REVIEW");
        assertThat(replayResult.path("runId").asLong()).isEqualTo(result.path("runId").asLong());
        assertThat(jdbc.queryForObject(
            "select count(*) from script_asset_normalization_run where tenant_id = 9101", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from script_asset_candidate where tenant_id = 9101 and name is not null", Integer.class)).isEqualTo(3);
        assertThat(canonicalAssetCount()).isZero();
    }

    @Test
    void retainsInvalidCandidatesAsBusinessFailureWithoutNullNameInsert() throws Exception {
        Object service = service();
        Object persisted = method(service).invoke(service,
            9201L, 9202L, 9203L, 9204L, 9205L, 9206L, 9207L, 9208L, 9209L,
            "recognition-attempt-invalid",
            "{\"characters\":[{}],\"scenes\":[],\"props\":[]}");
        JsonNode result = objectMapper.valueToTree(persisted);

        assertThat(result.path("status").asText()).isEqualTo("BUSINESS_FAILED");
        assertThat(jdbc.queryForObject(
            "select count(*) from script_asset_candidate where tenant_id = 9201 and validation_status = 'INVALID'",
            Integer.class)).isEqualTo(1);
        assertThat(canonicalAssetCount()).isZero();
    }

    @Test
    void linksThePersistedAnalysisResultWithoutChangingAnIdempotentRun() throws Exception {
        Object service = service();
        Object persisted = method(service).invoke(service,
            9301L, 9302L, 9303L, 9304L, 9305L, 9306L, 9307L, 9308L, 9309L,
            "recognition-attempt-linked",
            "{\"characters\":[\"林夏\"],\"scenes\":[],\"props\":[]}");
        long runId = objectMapper.valueToTree(persisted).path("runId").asLong();

        Method attach = service.getClass().getMethod(
            "attachAnalysisResult", Long.class, Long.class, Long.class);
        attach.invoke(service, 9301L, runId, 9310L);
        attach.invoke(service, 9301L, runId, 9310L);

        assertThat(jdbc.queryForObject(
            "select analysis_result_id from script_asset_normalization_run where id = ?",
            Long.class, runId)).isEqualTo(9310L);
    }

    private Object service() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.antshorttv.script.ScriptAssetNormalizationService");
        } catch (ClassNotFoundException exception) {
            assertThat(exception).as("ScriptAssetNormalizationService must exist").isNull();
            throw exception;
        }
        return applicationContext.getBean(type);
    }

    private Method method(Object service) throws NoSuchMethodException {
        return service.getClass().getMethod("normalizeAndPersist",
            Long.class, Long.class, Long.class, Long.class, Long.class, Long.class,
            Long.class, Long.class, Long.class, String.class, String.class);
    }

    private Integer canonicalAssetCount() {
        return jdbc.queryForObject("""
            select (select count(*) from character_asset where tenant_id in (9101, 9201))
                 + (select count(*) from scene_asset where tenant_id in (9101, 9201))
                 + (select count(*) from prop_asset where tenant_id in (9101, 9201))
            """, Integer.class);
    }
}
