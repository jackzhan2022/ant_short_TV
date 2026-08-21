package com.antshorttv.video;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiTaskExecutionSupport {
    private final JdbcTemplate jdbcTemplate;

    public AiTaskExecutionSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String idempotencyKey(String workflowType, Long taskId, String phase, int executionVersion) {
        return "%s:%d:%s:%d".formatted(workflowType, taskId, phase, executionVersion);
    }

    public boolean isRetryable(String status, Set<String> retryableStatuses) {
        return status != null && retryableStatuses.contains(status);
    }

    public ClaimResult claimVideoDecompositionEpisode(
        Long episodeId,
        String expectedStatus,
        String runningStatus,
        String phase,
        Duration timeout
    ) {
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
            update video_decomposition_episode
               set status = ?,
                   execution_token = ?,
                   execution_phase = ?,
                   execution_version = coalesce(execution_version, 0) + 1,
                   claimed_at = ?,
                   heartbeat_at = ?,
                   execution_timeout_at = ?,
                   retryable = false,
                   updated_at = ?
             where id = ?
               and status = ?
               and execution_token is null
            """,
            runningStatus,
            token,
            phase,
            now,
            now,
            now.plus(timeout),
            now,
            episodeId,
            expectedStatus
        );
        if (updated != 1) {
            return ClaimResult.notClaimed();
        }
        Integer executionVersion = jdbcTemplate.queryForObject(
            "select execution_version from video_decomposition_episode where id = ?",
            Integer.class,
            episodeId
        );
        int version = executionVersion == null ? 0 : executionVersion;
        return new ClaimResult(true, token, version, idempotencyKey("VIDEO_DECOMPOSITION", episodeId, phase, version));
    }

    public ClaimResult claimAiVideoTask(Long taskId, String expectedStatus, String phase, Duration timeout) {
        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
            update ai_video_task
               set execution_token = ?,
                   execution_phase = ?,
                   execution_version = coalesce(execution_version, 0) + 1,
                   claimed_at = ?,
                   heartbeat_at = ?,
                   execution_timeout_at = ?,
                   retryable = false,
                   updated_at = ?
             where id = ?
               and status = ?
               and execution_token is null
               and deleted_at is null
            """,
            token,
            phase,
            now,
            now,
            now.plus(timeout),
            now,
            taskId,
            expectedStatus
        );
        if (updated != 1) {
            return ClaimResult.notClaimed();
        }
        Integer executionVersion = jdbcTemplate.queryForObject(
            "select execution_version from ai_video_task where id = ?",
            Integer.class,
            taskId
        );
        int version = executionVersion == null ? 0 : executionVersion;
        return new ClaimResult(true, token, version, idempotencyKey("AI_VIDEO_TASK", taskId, phase, version));
    }

    public Long createVideoDecompositionAttempt(
        Long episodeId,
        int attemptNo,
        String phase,
        String status,
        String idempotencyKey,
        boolean retryable
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
            insert into video_decomposition_attempt
              (episode_id, attempt_no, phase, status, idempotency_key, retryable, started_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """, episodeId, attemptNo, phase, status, idempotencyKey, retryable, now);
        return jdbcTemplate.queryForObject(
            "select max(id) from video_decomposition_attempt where episode_id = ? and phase = ?",
            Long.class,
            episodeId,
            phase
        );
    }

    public void finishVideoDecompositionAttempt(
        Long attemptId,
        String status,
        String providerRequestId,
        Long aiCallLogId,
        boolean retryable,
        String errorCode,
        String errorMessage
    ) {
        jdbcTemplate.update("""
            update video_decomposition_attempt
               set status = ?,
                   provider_request_id = ?,
                   ai_call_log_id = ?,
                   retryable = ?,
                   error_code = ?,
                   error_message = ?,
                   finished_at = ?
             where id = ?
            """,
            status,
            providerRequestId,
            aiCallLogId,
            retryable,
            errorCode,
            errorMessage,
            LocalDateTime.now(),
            attemptId
        );
    }

    public Long createAiVideoTaskAttempt(
        Long taskId,
        int attemptNo,
        String phase,
        String status,
        String idempotencyKey,
        boolean retryable
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
            insert into ai_video_task_attempt
              (task_id, attempt_no, phase, status, idempotency_key, retryable, started_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """, taskId, attemptNo, phase, status, idempotencyKey, retryable, now);
        return jdbcTemplate.queryForObject(
            "select max(id) from ai_video_task_attempt where task_id = ? and phase = ?",
            Long.class,
            taskId,
            phase
        );
    }

    public void finishAiVideoTaskAttempt(
        Long attemptId,
        String status,
        String providerRequestId,
        Long aiCallLogId,
        boolean retryable,
        String errorCode,
        String errorMessage
    ) {
        jdbcTemplate.update("""
            update ai_video_task_attempt
               set status = ?,
                   provider_request_id = ?,
                   ai_call_log_id = ?,
                   retryable = ?,
                   error_code = ?,
                   error_message = ?,
                   finished_at = ?
             where id = ?
            """,
            status,
            providerRequestId,
            aiCallLogId,
            retryable,
            errorCode,
            errorMessage,
            LocalDateTime.now(),
            attemptId
        );
    }

    public void clearAiVideoTaskClaim(Long taskId, boolean retryable) {
        jdbcTemplate.update("""
            update ai_video_task
               set execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   execution_timeout_at = null,
                   retryable = ?
             where id = ?
            """, retryable, taskId);
    }

    public int timeoutVideoDecompositionExecutions() {
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update("""
            update video_decomposition_episode
               set status = 'FAILED',
                   error_code = 'AI_TASK_TIMEOUT',
                   error_message = 'AI task execution timed out.',
                   execution_token = null,
                   execution_phase = null,
                   heartbeat_at = null,
                   retryable = true,
                   updated_at = ?
             where execution_token is not null
               and execution_timeout_at is not null
               and execution_timeout_at <= ?
            """, now, now);
    }

    public record ClaimResult(
        boolean claimed,
        String executionToken,
        int executionVersion,
        String idempotencyKey
    ) {
        static ClaimResult notClaimed() {
            return new ClaimResult(false, null, 0, null);
        }
    }
}
