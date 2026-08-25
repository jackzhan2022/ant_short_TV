package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiExecutionClaimService {
    private final AiExecutionTaskMapper taskMapper;
    private final AiExecutionAttemptMapper attemptMapper;
    private final int maxConcurrentPerTenant;

    public AiExecutionClaimService(
        AiExecutionTaskMapper taskMapper,
        AiExecutionAttemptMapper attemptMapper,
        @Value("${ai.execution.max-concurrent-per-tenant:5}") int maxConcurrentPerTenant
    ) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.maxConcurrentPerTenant = maxConcurrentPerTenant;
    }

    @Transactional
    public AiExecutionClaim claim(Long executionId, String claimToken, LocalDateTime now, Duration timeout) {
        AiExecutionTaskEntity candidate = taskMapper.selectById(executionId);
        if (candidate == null || !AiExecutionStatus.PENDING.name().equals(candidate.status)) {
            return null;
        }
        Long running = taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", candidate.tenantId)
            .eq("status", AiExecutionStatus.RUNNING.name()));
        if (running >= maxConcurrentPerTenant) {
            return null;
        }
        int updated = taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", AiExecutionStatus.RUNNING.name())
            .set("claim_token", claimToken)
            .set("claimed_at", now)
            .set("heartbeat_at", now)
            .set("claim_expires_at", now.plus(timeout))
            .set("started_at", now)
            .set("updated_at", now)
            .eq("id", executionId)
            .eq("status", AiExecutionStatus.PENDING.name())
            .isNull("claim_token")
            .and(wrapper -> wrapper.isNull("next_run_at").or().le("next_run_at", now)));
        if (updated == 0) {
            return null;
        }
        int attemptNo = Math.toIntExact(attemptMapper.selectCount(
            new QueryWrapper<AiExecutionAttemptEntity>().eq("execution_id", executionId)
        ) + 1);
        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.executionId = executionId;
        attempt.executionVersion = candidate.executionVersion;
        attempt.phase = candidate.phase;
        attempt.attemptNo = attemptNo;
        attempt.status = AiExecutionAttemptStatus.STARTED.name();
        attempt.idempotencyKey = candidate.clientIdempotencyKey + ":" + candidate.executionVersion + ":"
            + candidate.phase + ":" + attemptNo;
        attempt.providerContacted = false;
        attempt.retryable = candidate.retryable;
        attempt.retryCount = attemptNo - 1;
        attempt.startedAt = now;
        attemptMapper.insert(attempt);
        return new AiExecutionClaim(executionId, attempt.id, claimToken, candidate.executionVersion, candidate.phase);
    }

    @Transactional
    public boolean heartbeat(Long executionId, String claimToken, LocalDateTime now, Duration timeout) {
        return taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("heartbeat_at", now)
            .set("claim_expires_at", now.plus(timeout))
            .set("updated_at", now)
            .eq("id", executionId)
            .eq("status", AiExecutionStatus.RUNNING.name())
            .eq("claim_token", claimToken)) == 1;
    }

    @Transactional
    public void markSucceeded(
        Long executionId,
        Long attemptId,
        String claimToken,
        String resultType,
        Long resultId,
        LocalDateTime now
    ) {
        int updated = taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", AiExecutionStatus.SUCCEEDED.name())
            .set("progress", 100)
            .set("result_type", resultType)
            .set("result_id", resultId)
            .set("claim_token", null)
            .set("claim_expires_at", null)
            .set("completed_at", now)
            .set("updated_at", now)
            .eq("id", executionId)
            .eq("status", AiExecutionStatus.RUNNING.name())
            .eq("claim_token", claimToken));
        if (updated == 0) {
            throw new AiExecutionClaimLostException(executionId);
        }
        attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", AiExecutionAttemptStatus.SUCCEEDED.name())
            .set("finished_at", now)
            .eq("id", attemptId)
            .eq("execution_id", executionId)
            .eq("status", AiExecutionAttemptStatus.STARTED.name()));
    }

    @Transactional
    public void markFailed(
        Long executionId,
        Long attemptId,
        String claimToken,
        String errorCode,
        String errorMessage,
        AiExecutionRetryPolicy retryPolicy,
        LocalDateTime now
    ) {
        AiExecutionAttemptEntity attempt = attemptMapper.selectById(attemptId);
        boolean retry = attempt != null && attempt.attemptNo < retryPolicy.maxAttempts();
        String status = retry ? AiExecutionStatus.PENDING.name() : AiExecutionStatus.FAILED.name();
        UpdateWrapper<AiExecutionTaskEntity> update = new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", status)
            .set("claim_token", null)
            .set("claim_expires_at", null)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("updated_at", now)
            .eq("id", executionId)
            .eq("status", AiExecutionStatus.RUNNING.name())
            .eq("claim_token", claimToken);
        if (retry) {
            update.set("next_run_at", now.plus(retryPolicy.delay()));
        } else {
            update.set("completed_at", now);
        }
        if (taskMapper.update(null, update) == 0) {
            throw new AiExecutionClaimLostException(executionId);
        }
        attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", AiExecutionAttemptStatus.FAILED.name())
            .set("retryable", retry)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("next_retry_at", retry ? now.plus(retryPolicy.delay()) : null)
            .set("finished_at", now)
            .eq("id", attemptId)
            .eq("execution_id", executionId));
    }

    @Transactional
    public int recoverExpiredClaims(LocalDateTime now) {
        List<AiExecutionTaskEntity> expired = taskMapper.selectList(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("status", AiExecutionStatus.RUNNING.name())
            .le("claim_expires_at", now));
        int recovered = 0;
        for (AiExecutionTaskEntity task : expired) {
            String nextStatus = Boolean.TRUE.equals(task.retryable)
                ? AiExecutionStatus.PENDING.name()
                : AiExecutionStatus.TIMED_OUT.name();
            UpdateWrapper<AiExecutionTaskEntity> update = new UpdateWrapper<AiExecutionTaskEntity>()
                .set("status", nextStatus)
                .set("claim_token", null)
                .set("claimed_at", null)
                .set("heartbeat_at", null)
                .set("claim_expires_at", null)
                .set("error_code", "EXECUTION_TIMEOUT")
                .set("error_message", "Execution claim expired before completion.")
                .set("updated_at", now)
                .eq("id", task.id)
                .eq("status", AiExecutionStatus.RUNNING.name())
                .eq("claim_token", task.claimToken)
                .le("claim_expires_at", now);
            if (Boolean.TRUE.equals(task.retryable)) {
                update.set("next_run_at", now);
            } else {
                update.set("completed_at", now);
            }
            if (taskMapper.update(null, update) == 1) {
                attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
                    .set("status", AiExecutionAttemptStatus.TIMED_OUT.name())
                    .set("error_code", "EXECUTION_TIMEOUT")
                    .set("error_message", "Execution claim expired before completion.")
                    .set("finished_at", now)
                    .eq("execution_id", task.id)
                    .eq("status", AiExecutionAttemptStatus.STARTED.name()));
                recovered++;
            }
        }
        return recovered;
    }
}
