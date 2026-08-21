package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiTaskExecutionSupportTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiTaskExecutionSupport executionSupport;

    @Test
    void buildsStableIdempotencyKeyForTaskPhaseAndVersion() {
        String first = executionSupport.idempotencyKey("VIDEO_DECOMPOSITION", 42L, "VIDEO_ANALYSIS", 3);
        String second = executionSupport.idempotencyKey("VIDEO_DECOMPOSITION", 42L, "VIDEO_ANALYSIS", 3);
        String regenerated = executionSupport.idempotencyKey("VIDEO_DECOMPOSITION", 42L, "VIDEO_ANALYSIS", 4);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(regenerated);
        assertThat(first).contains("VIDEO_DECOMPOSITION", "42", "VIDEO_ANALYSIS", "3");
    }

    @Test
    void claimsVideoDecompositionEpisodeOnlyOnce() {
        Long batchId = insertBatch();
        Long episodeId = insertEpisode(batchId);

        AiTaskExecutionSupport.ClaimResult first = executionSupport.claimVideoDecompositionEpisode(
            episodeId,
            "PENDING_ANALYSIS",
            "ANALYZING",
            "VIDEO_ANALYSIS",
            Duration.ofMinutes(15)
        );
        AiTaskExecutionSupport.ClaimResult second = executionSupport.claimVideoDecompositionEpisode(
            episodeId,
            "PENDING_ANALYSIS",
            "ANALYZING",
            "VIDEO_ANALYSIS",
            Duration.ofMinutes(15)
        );

        assertThat(first.claimed()).isTrue();
        assertThat(first.executionToken()).isNotBlank();
        assertThat(first.idempotencyKey()).contains("VIDEO_DECOMPOSITION", episodeId.toString(), "VIDEO_ANALYSIS");
        assertThat(second.claimed()).isFalse();

        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        assertThat(episode.get("status")).isEqualTo("ANALYZING");
        assertThat(episode.get("execution_token")).isEqualTo(first.executionToken());
        assertThat(episode.get("execution_phase")).isEqualTo("VIDEO_ANALYSIS");
        assertThat(episode.get("claimed_at")).isNotNull();
        assertThat(episode.get("execution_timeout_at")).isNotNull();
    }

    @Test
    void retryEligibilityIsExplicitlyStateBased() {
        assertThat(executionSupport.isRetryable("FAILED", Set.of("FAILED"))).isTrue();
        assertThat(executionSupport.isRetryable("CONFIRMED", Set.of("FAILED"))).isFalse();
        assertThat(executionSupport.isRetryable("ANALYZING", Set.of("FAILED"))).isFalse();
    }

    @Test
    void recordsAttemptLifecycleWithDiagnostics() {
        Long batchId = insertBatch();
        Long episodeId = insertEpisode(batchId);
        String idempotencyKey = executionSupport.idempotencyKey("VIDEO_DECOMPOSITION", episodeId, "VIDEO_ANALYSIS", 1);

        Long attemptId = executionSupport.createVideoDecompositionAttempt(
            episodeId,
            1,
            "VIDEO_ANALYSIS",
            "RUNNING",
            idempotencyKey,
            false
        );
        executionSupport.finishVideoDecompositionAttempt(
            attemptId,
            "FAILED",
            "provider-request-1",
            99L,
            true,
            "AI_RESPONSE_INVALID",
            "业务解析失败"
        );

        var attempt = jdbc.queryForMap("select * from video_decomposition_attempt where id = ?", attemptId);
        assertThat(attempt.get("status")).isEqualTo("FAILED");
        assertThat(attempt.get("idempotency_key")).isEqualTo(idempotencyKey);
        assertThat(attempt.get("provider_request_id")).isEqualTo("provider-request-1");
        assertThat(attempt.get("ai_call_log_id")).isEqualTo(99L);
        assertThat(attempt.get("retryable")).isEqualTo(true);
        assertThat(attempt.get("error_code")).isEqualTo("AI_RESPONSE_INVALID");
        assertThat(attempt.get("finished_at")).isNotNull();
    }

    @Test
    void marksTimedOutVideoDecompositionEpisodeRetryable() {
        Long batchId = insertBatch();
        Long episodeId = insertEpisode(batchId);
        executionSupport.claimVideoDecompositionEpisode(
            episodeId,
            "PENDING_ANALYSIS",
            "ANALYZING",
            "VIDEO_ANALYSIS",
            Duration.ofSeconds(-1)
        );

        int timedOut = executionSupport.timeoutVideoDecompositionExecutions();

        assertThat(timedOut).isEqualTo(1);
        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        assertThat(episode.get("status")).isEqualTo("FAILED");
        assertThat(episode.get("error_code")).isEqualTo("AI_TASK_TIMEOUT");
        assertThat(episode.get("retryable")).isEqualTo(true);
        assertThat(episode.get("execution_token")).isNull();
    }

    @Test
    void claimsAiVideoTaskOnlyOnce() {
        Long taskId = insertAiVideoTask();

        AiTaskExecutionSupport.ClaimResult first = executionSupport.claimAiVideoTask(
            taskId,
            "GENERATING",
            "VIDEO_QUERY",
            Duration.ofMinutes(5)
        );
        AiTaskExecutionSupport.ClaimResult second = executionSupport.claimAiVideoTask(
            taskId,
            "GENERATING",
            "VIDEO_QUERY",
            Duration.ofMinutes(5)
        );

        assertThat(first.claimed()).isTrue();
        assertThat(first.idempotencyKey()).contains("AI_VIDEO_TASK", taskId.toString(), "VIDEO_QUERY");
        assertThat(second.claimed()).isFalse();

        Long attemptId = executionSupport.createAiVideoTaskAttempt(
            taskId,
            1,
            "VIDEO_QUERY",
            "RUNNING",
            first.idempotencyKey(),
            false
        );
        executionSupport.finishAiVideoTaskAttempt(attemptId, "SUCCEEDED", "video-query-1", 100L, false, null, null);
        executionSupport.clearAiVideoTaskClaim(taskId, false);

        var task = jdbc.queryForMap("select * from ai_video_task where id = ?", taskId);
        assertThat(task.get("execution_token")).isNull();
        assertThat(task.get("retryable")).isEqualTo(false);
        var attempt = jdbc.queryForMap("select * from ai_video_task_attempt where id = ?", attemptId);
        assertThat(attempt.get("status")).isEqualTo("SUCCEEDED");
        assertThat(attempt.get("idempotency_key")).isEqualTo(first.idempotencyKey());
    }

    private Long insertBatch() {
        jdbc.update("""
            insert into video_decomposition_batch
              (tenant_id, project_id, name, model_id, status, total_episodes, completed_episodes, failed_episodes, created_by, created_at, updated_at)
            values (8101, 8201, 'claim batch', null, 'PENDING_ANALYSIS', 1, 0, 0, 8301, now(), now())
            """);
        return jdbc.queryForObject("select max(id) from video_decomposition_batch where tenant_id = 8101", Long.class);
    }

    private Long insertEpisode(Long batchId) {
        jdbc.update("""
            insert into video_decomposition_episode
              (batch_id, tenant_id, project_id, episode_no, source_file_name, storage_path, mime_type, file_size,
               duration_seconds, status, analysis_version, draft_status, draft_version, created_by, created_at, updated_at)
            values (?, 8101, 8201, 1, 'episode.mp4', 'https://cdn.example.com/episode.mp4', 'video/mp4', 1024,
               60, 'PENDING_ANALYSIS', 0, 'NOT_STARTED', 0, 8301, now(), now())
            """, batchId);
        return jdbc.queryForObject("select max(id) from video_decomposition_episode where batch_id = ?", Long.class, batchId);
    }

    private Long insertAiVideoTask() {
        jdbc.update("""
            insert into ai_video_task
              (tenant_id, project_id, storyboard_id, service_config_id, provider_code, model, prompt,
               first_frame_url, duration_seconds, aspect_ratio, status, request_hash, poll_retry_count,
               created_by, created_at, updated_at, next_poll_at)
            values
              (9101, 9201, 9301, 9401, 'mock', 'video-model', 'prompt',
               'https://cdn.example.com/first-frame.jpg', 5, '9:16', 'GENERATING', 'hash-claim', 0,
               9501, now(), now(), now())
            """);
        return jdbc.queryForObject("select max(id) from ai_video_task where tenant_id = 9101", Long.class);
    }
}
