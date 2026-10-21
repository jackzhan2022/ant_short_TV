package com.antshorttv.execution;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class AiExecutionClaimService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.antshorttv.querycache.QueryResultCache queryCache = com.antshorttv.querycache.QueryResultCache.disabled();
    private final AiExecutionTaskMapper taskMapper;
    private final AiExecutionAttemptMapper attemptMapper;
    private final AiConcurrencyConfigService config;
    private final JdbcTemplate jdbc;

    public AiExecutionClaimService(
        AiExecutionTaskMapper taskMapper,
        AiExecutionAttemptMapper attemptMapper,
        JdbcTemplate jdbc,
        AiConcurrencyConfigService config
    ) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.jdbc = jdbc;
        this.config = config;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiExecutionClaim claim(Long executionId, String claimToken, LocalDateTime now, Duration timeout) {
        AiExecutionTaskEntity candidate = taskMapper.selectById(executionId);
        if (candidate == null || !AiExecutionStatus.PENDING.name().equals(candidate.status)) {
            return null;
        }
        boolean publication = "ai_image_generate".equals(candidate.scene)
            && taskMapper.countImagePublication(executionId) == 1L;
        if (!publication) {
            String type = candidate.taskType == null
                ? AiExecutionTaskType.classify(candidate.capability, candidate.scene, candidate.businessType).name()
                : candidate.taskType;
            var settings = config.lockForAdmission();
            lockTenantQuota(candidate.tenantId, type);
            int tenantLimit = switch (AiExecutionTaskType.valueOf(type)) {
                case TEXT -> settings.teamTextLimit();
                case IMAGE -> settings.teamImageLimit();
                case VIDEO -> settings.teamVideoLimit();
            };
            Long running = taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
                .eq("tenant_id", candidate.tenantId)
                .eq("task_type", type)
                .eq("status", AiExecutionStatus.RUNNING.name())
                .apply("not " + AiExecutionTaskMapper.IMAGE_PUBLICATION));
            if (running >= tenantLimit) {
                return null;
            }
            Long modelId = candidate.resolvedModelId == null
                ? candidate.requestedModelId : candidate.resolvedModelId;
            if (modelId != null && modelCapacityReached(modelId, settings.modelLimit())) {
                return null;
            }
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

    private void lockTenantQuota(Long tenantId, String type) {
        jdbc.update("""
            insert into ai_execution_tenant_quota(tenant_id,task_type) values (?,?)
            on duplicate key update tenant_id=values(tenant_id)
            """, tenantId, type);
        jdbc.queryForObject("select tenant_id from ai_execution_tenant_quota where tenant_id=? and task_type=? for update",
            Long.class, tenantId, type);
    }

    private boolean modelCapacityReached(Long modelId, int maxConcurrentPerModel) {
        jdbc.update("""
                insert into ai_model_execution_quota(model_id, concurrency_limit, updated_at)
                values (?, ?, now())
                on duplicate key update model_id=values(model_id)
                """, modelId, maxConcurrentPerModel);
        Integer limit = jdbc.queryForObject("""
            select concurrency_limit from ai_model_execution_quota
             where model_id = ? for update
            """, Integer.class, modelId);
        Long running = taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("status", AiExecutionStatus.RUNNING.name())
            .apply("not " + AiExecutionTaskMapper.IMAGE_PUBLICATION)
            .and(scope -> scope.eq("resolved_model_id", modelId)
                .or(nested -> nested.isNull("resolved_model_id").eq("requested_model_id", modelId))));
        return running >= (limit == null ? maxConcurrentPerModel : limit);
    }

    /** An accepted remote video keeps its quota while no local submission thread is running. */
    @Transactional
    public void handoffExternal(AiExecutionClaim claim, LocalDateTime now) {
        int updated = taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("phase", "VIDEO_QUERY").set("claim_token", null).set("claim_expires_at", null)
            .set("updated_at", now)
            .eq("id", claim.executionId()).eq("execution_version", claim.executionVersion())
            .eq("status", "RUNNING").eq("claim_token", claim.claimToken()));
        if (updated != 1) throw new AiExecutionClaimLostException(claim.executionId());
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

    public void requireActive(AiExecutionClaim claim) {
        if (claim == null) {
            return;
        }
        Long activeTask = taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("id", claim.executionId())
            .eq("execution_version", claim.executionVersion())
            .eq("status", AiExecutionStatus.RUNNING.name())
            .eq("claim_token", claim.claimToken()));
        Long activeAttempt = attemptMapper.selectCount(new QueryWrapper<AiExecutionAttemptEntity>()
            .eq("id", claim.attemptId())
            .eq("execution_id", claim.executionId())
            .eq("execution_version", claim.executionVersion())
            .eq("status", AiExecutionAttemptStatus.STARTED.name()));
        if (activeTask != 1L || activeAttempt != 1L) {
            throw new AiExecutionClaimLostException(claim.executionId());
        }
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
    public void defer(
        Long executionId,
        Long attemptId,
        String claimToken,
        String errorCode,
        String errorMessage,
        LocalDateTime nextRunAt,
        LocalDateTime now
    ) {
        int updated = taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", AiExecutionStatus.PENDING.name())
            .set("claim_token", null)
            .set("claim_expires_at", null)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("next_run_at", nextRunAt)
            .set("updated_at", now)
            .eq("id", executionId)
            .eq("status", AiExecutionStatus.RUNNING.name())
            .eq("claim_token", claimToken));
        if (updated == 0) {
            throw new AiExecutionClaimLostException(executionId);
        }
        attemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", AiExecutionAttemptStatus.DEFERRED.name())
            .set("retryable", true)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("next_retry_at", nextRunAt)
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
        Long failedAttempts = attemptMapper.selectCount(new QueryWrapper<AiExecutionAttemptEntity>()
            .eq("execution_id",executionId).eq("execution_version",attempt == null ? null : attempt.executionVersion)
            .in("status","FAILED","TIMED_OUT"));
        boolean retry = attempt != null && failedAttempts + 1 < retryPolicy.maxAttempts();
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
                if(queryCache.isEnabled() && task.tenantId!=null) {
                    queryCache.invalidateAfterCommit(com.antshorttv.querycache.QueryResultCache.Scope.tenant(task.tenantId),
                        com.antshorttv.querycache.QueryResultCache.Region.TASKS);
                    queryCache.invalidateProjectAfterCommit(task.tenantId,task.projectId,
                        com.antshorttv.querycache.QueryResultCache.Region.ANALYSIS);
                }
                recovered++;
            }
        }
        return recovered;
    }

    @Transactional
    public void defer(AiExecutionClaim claim,String reason,LocalDateTime now) {
        int updated=taskMapper.update(null,new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status","PENDING").set("next_run_at",now.plusSeconds(10))
            .set("claim_token",null).set("claim_expires_at",null).set("error_code","RESOURCE_WAIT")
            .set("error_message",reason).set("updated_at",now)
            .eq("id",claim.executionId()).eq("execution_version",claim.executionVersion())
            .eq("status","RUNNING").eq("claim_token",claim.claimToken()));
        if(updated!=1)throw new AiExecutionClaimLostException(claim.executionId());
        attemptMapper.update(null,new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status","CANCELED").set("error_code","RESOURCE_WAIT").set("error_message",reason)
            .set("finished_at",now).eq("id",claim.attemptId()).eq("status","STARTED"));
    }
}
