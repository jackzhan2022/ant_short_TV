package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.common.ErrorCode;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AiCallLogWriterTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiCallLogWriter writer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void recordsSuccessAndBusinessFailureWithSharedMetadata() {
        AiModelRoute route = route();
        AiInvocationLogRequest request = new AiInvocationLogRequest(
            new AiContext(
                701L, 702L, 703L, 704L, 801L, "video_understanding", "trace-logs",
                705L, 706L, 2, "POLL", "execution-705-v2-poll"
            ),
            route,
            AiCapability.VIDEO_UNDERSTANDING,
            "https://cdn.example.com/episode.mp4",
            "{\"characters\":[]}",
            "SUCCESS",
            null,
            100L,
            "provider-req-1",
            7,
            8,
            15
        );

        Long logId = writer.record(request);
        writer.markBusinessFailure(logId, ErrorCode.AI_RESPONSE_INVALID, "JSON 字段缺失");

        Map<String, Object> log = jdbc.queryForMap("select * from ai_call_log where id = ?", logId);
        assertThat(log.get("service_type")).isEqualTo("VIDEO_UNDERSTANDING");
        assertThat(log.get("business_scene")).isEqualTo("video_understanding");
        assertThat(log.get("task_id")).isEqualTo(704L);
        assertThat(log.get("trace_id")).isEqualTo("trace-logs");
        assertThat(log.get("provider_request_id")).isEqualTo("provider-req-1");
        assertThat(log.get("execution_id")).isEqualTo(705L);
        assertThat(log.get("attempt_id")).isEqualTo(706L);
        assertThat(log.get("execution_version")).isEqualTo(2);
        assertThat(log.get("phase")).isEqualTo("POLL");
        assertThat(log.get("idempotency_key")).isEqualTo("execution-705-v2-poll");
        assertThat(log.get("transport_outcome")).isEqualTo("SUCCEEDED");
        assertThat(log.get("business_outcome")).isEqualTo("FAILED");
        assertThat(log.get("total_tokens")).isEqualTo(15);
        assertThat(log.get("status")).isEqualTo("FAILED");
        assertThat((String) log.get("error_message")).contains("AI_RESPONSE_INVALID", "JSON 字段缺失");
    }

    @Test
    void recordsFailureEvenWhenCallerTransactionRollsBack() {
        AtomicReference<Long> logId = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            transaction.executeWithoutResult(status -> {
                logId.set(writer.record(AiInvocationLogRequest.failed(
                    new AiContext(711L, 712L, 713L, null, 811L, "character_extract", "trace-rollback"),
                    route(),
                    AiCapability.TEXT,
                    "剧本标题",
                    "AI_PROVIDER_ERROR Connection reset",
                    123L
                )));
                throw new IllegalStateException("rollback caller");
            });
        } catch (IllegalStateException ignored) {
        }

        Integer count = jdbc.queryForObject("select count(*) from ai_call_log where id = ?", Integer.class, logId.get());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void recordsAcceptedExternalWorkAsTransportSuccessAndBusinessPending() {
        AiContext context = new AiContext(
            721L, 722L, 723L, null, 801L, "image_generate", "trace-accepted",
            725L, 726L, 1, "SUBMIT", "execution-725-v1-submit"
        );

        Long logId = writer.record(AiInvocationLogRequest.accepted(
            context,
            route(),
            AiCapability.IMAGE,
            "生成角色图",
            120L,
            "provider-request-accepted",
            "external-task-accepted"
        ));

        Map<String, Object> log = jdbc.queryForMap("select * from ai_call_log where id = ?", logId);
        assertThat(log.get("status")).isEqualTo("ACCEPTED");
        assertThat(log.get("provider_request_id")).isEqualTo("provider-request-accepted");
        assertThat(log.get("external_task_id")).isEqualTo("external-task-accepted");
        assertThat(log.get("transport_outcome")).isEqualTo("SUCCEEDED");
        assertThat(log.get("business_outcome")).isEqualTo("PENDING");
    }

    @Test
    void redactsCredentialsFromRequestAndResponseSummaries() {
        Long logId = writer.record(new AiInvocationLogRequest(
            new AiContext(731L, 732L, 733L, null, 801L, "script_generate", "trace-redact"),
            route(),
            AiCapability.TEXT,
            "Authorization: Bearer sk-live-secret api_key=another-secret",
            "{\"apiKey\":\"secret-value\",\"text\":\"正常结果\"}",
            "SUCCESS",
            null,
            10L,
            null,
            null,
            null,
            null
        ));

        Map<String, Object> log = jdbc.queryForMap("select request_summary, response_summary from ai_call_log where id = ?", logId);
        assertThat((String) log.get("request_summary")).doesNotContain("sk-live-secret", "another-secret").contains("[REDACTED]");
        assertThat((String) log.get("response_summary")).doesNotContain("secret-value").contains("[REDACTED]");
    }

    private AiModelRoute route() {
        AiModelEntity model = new AiModelEntity();
        model.setId(801L);
        model.setName("Qwen3.7 Plus");
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(802L);
        provider.setCode("阿里云百炼");
        return new AiModelRoute(model, provider, new AiProviderConfigEntity(), null);
    }
}
