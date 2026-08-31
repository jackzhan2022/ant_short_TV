package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.ai.AiToolCall;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class WorkflowAgentRunRepositoryTest {
    @Autowired
    private WorkflowAgentRunRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsRedactedSnapshotsOrderedStepsCallReferencesAndFinalState() {
        long modelId = model();
        Long runId = repository.start(new WorkflowAgentRunStart(
            null, "temporary-agent", "TEST", 7L, 9L, 25L, 91L, 101L, modelId,
            new BigDecimal("0.2"), 2048, 3,
            "使用 apiKey=top-secret 和 sk-abcdef 完成任务",
            List.of(new WorkflowAgentSkillSnapshot("guide", "指南", "hash-1",
                "password: dont-log-this\n保留剧情")),
            List.of("read_episode_script")
        ));
        repository.recordModelStep(runId, 1, 501L,
            List.of(new AiToolCall("call-1", "read_episode_script", "{}")), null);
        repository.recordToolStep(runId, 2, "read_episode_script", "{}", "{\"content\":\"第一集\"}");
        repository.recordModelStep(runId, 3, 502L, List.of(), "改写完成");
        repository.complete(runId, "改写完成");

        WorkflowAgentRunDetail detail = repository.detail(7L, runId);
        assertThat(detail.status()).isEqualTo("SUCCESS");
        assertThat(detail.promptSnapshot()).contains("[REDACTED]").doesNotContain("top-secret", "sk-abcdef");
        assertThat(detail.skillSnapshots()).singleElement().satisfies(snapshot ->
            assertThat(snapshot.content()).contains("[REDACTED]").doesNotContain("dont-log-this"));
        assertThat(detail.toolCodes()).containsExactly("read_episode_script");
        assertThat(detail.steps()).extracting(WorkflowAgentRunStepView::stepNo).containsExactly(1, 2, 3);
        assertThat(detail.steps()).extracting(WorkflowAgentRunStepView::aiCallLogId)
            .containsExactly(501L, null, 502L);
        assertThat(detail.steps().get(0).outputJson())
            .contains("read_episode_script", "toolCalls", "content");
        assertThat(detail.steps().get(2).outputJson())
            .contains("改写完成", "toolCalls", "content");
        assertThat(detail.finalOutput()).isEqualTo("改写完成");
        assertThat(detail.finishedAt()).isNotNull();
        assertThat(repository.list(7L, "temporary-agent", 20)).extracting(WorkflowAgentRunSummary::id)
            .contains(runId);
        assertThat(repository.list(8L, "temporary-agent", 20)).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.detail(8L, runId))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
    }

    @Test
    void persistsNormalizedFailureAndFailedToolStep() {
        long modelId = model();
        Long runId = repository.start(new WorkflowAgentRunStart(
            null, "failed-agent", "FORMAL", 7L, 9L, 25L, 91L, null, modelId,
            new BigDecimal("0.2"), 2048, 3, "prompt", List.of(), List.of("save_episode_script")
        ));
        repository.recordFailedToolStep(runId, 1, "save_episode_script", "{}",
            "VALIDATION_ERROR", "内容无效");
        repository.fail(runId, "VALIDATION_ERROR", "内容无效");

        WorkflowAgentRunDetail detail = repository.detail(7L, runId);
        assertThat(detail.status()).isEqualTo("FAILED");
        assertThat(detail.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(detail.steps()).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo("FAILED");
            assertThat(step.errorCode()).isEqualTo("VALIDATION_ERROR");
        });
    }

    @Test
    void keepsOversizedSkillSnapshotsAsReadableJson() {
        long modelId = model();
        Long runId = repository.start(new WorkflowAgentRunStart(
            null, "large-snapshot-agent", "TEST", 7L, 9L, null, null, null, modelId,
            new BigDecimal("0.2"), 2048, 3, "prompt",
            List.of(new WorkflowAgentSkillSnapshot(
                "large-guide", "Large guide", "hash-large", "x".repeat(300_000))),
            List.of()
        ));

        WorkflowAgentRunDetail detail = repository.detail(7L, runId);
        assertThat(detail.skillSnapshots()).singleElement().satisfies(snapshot ->
            assertThat(snapshot.content()).endsWith("...[TRUNCATED]"));
    }

    private long model() {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        String code = "run-model-" + UUID.randomUUID();
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, ?, ?, ?, 'TEXT', 'ENABLED', false, 999, now(), now())
            """, providerId, code, code, code);
        return jdbc.queryForObject("select id from ai_model where code = ?", Long.class, code);
    }
}
