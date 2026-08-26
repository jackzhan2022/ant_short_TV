package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.accounting.ModelBillingMissingException;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiExecutionCoreServiceTest {

    @Autowired
    private AiExecutionService executionService;

    @Autowired
    private AiExecutionClaimService claimService;

    @Autowired
    private AiExecutionTaskMapper taskMapper;

    @Autowired
    private AiExecutionAttemptMapper attemptMapper;

    @Autowired
    private AiPointReservationMapper reservationMapper;

    @Test
    void missingDualPriceRejectsBeforeTaskAndReservationCreation() {
        AiExecutionCreateCommand command = new AiExecutionCreateCommand(
            8601L, 8602L, null, "TEST_BILLING", "TEXT", "TEST_RESOURCE", 8603L,
            999901L, "SUBMIT", "missing-dual-price", "trace-missing-dual-price", true, null
        );

        assertThatThrownBy(() -> executionService.createWithReservation(
            command, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of()
        )).isInstanceOf(ModelBillingMissingException.class);
        assertThat(taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("client_idempotency_key", "missing-dual-price"))).isZero();
        assertThat(reservationMapper.selectCount(new QueryWrapper<AiPointReservationEntity>()
            .eq("idempotency_key", "execution:missing-dual-price"))).isZero();
    }

    @Test
    void creationIsIdempotentForTenantSceneAndClientKey() {
        AiExecutionCreateCommand command = command(8101L, "idempotent-create", false);

        AiExecutionTaskEntity first = executionService.create(command);
        AiExecutionTaskEntity duplicate = executionService.create(command);

        assertThat(duplicate.id).isEqualTo(first.id);
        assertThat(taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", command.tenantId())
            .eq("scene", command.scene())
            .eq("client_idempotency_key", command.clientIdempotencyKey()))).isEqualTo(1);
    }

    @Test
    void claimIsAtomicAndCreatesOneAttempt() {
        AiExecutionTaskEntity task = executionService.create(command(8201L, "atomic-claim", false));
        LocalDateTime now = LocalDateTime.now();

        AiExecutionClaim first = claimService.claim(task.id, "worker-a", now, Duration.ofMinutes(5));
        AiExecutionClaim second = claimService.claim(task.id, "worker-b", now, Duration.ofMinutes(5));

        assertThat(first).isNotNull();
        assertThat(second).isNull();
        assertThat(attemptMapper.selectByExecutionId(task.id)).hasSize(1);
        assertThat(taskMapper.selectById(task.id).claimToken).isEqualTo("worker-a");
    }

    @Test
    void staleWorkerCannotHeartbeatOrFinalizeAfterClaimLoss() {
        AiExecutionTaskEntity task = executionService.create(command(8301L, "claim-loss", false));
        LocalDateTime now = LocalDateTime.now();
        AiExecutionClaim claim = claimService.claim(task.id, "current-worker", now, Duration.ofMinutes(5));

        assertThat(claimService.heartbeat(task.id, "stale-worker", now.plusSeconds(30), Duration.ofMinutes(5)))
            .isFalse();
        assertThatThrownBy(() -> claimService.markSucceeded(
            task.id,
            claim.attemptId(),
            "stale-worker",
            "AI_IMAGE_RESULT",
            9001L,
            now.plusMinutes(1)
        )).isInstanceOf(AiExecutionClaimLostException.class);
        assertThat(taskMapper.selectById(task.id).status).isEqualTo(AiExecutionStatus.RUNNING.name());
    }

    @Test
    void expiredClaimsBecomeRetryableOrTimedOutWithoutStayingRunning() {
        AiExecutionTaskEntity retryable = executionService.create(command(8401L, "retryable-timeout", true));
        AiExecutionTaskEntity terminal = executionService.create(command(8402L, "terminal-timeout", false));
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        claimService.claim(retryable.id, "retry-worker", now, Duration.ofMinutes(1));
        claimService.claim(terminal.id, "terminal-worker", now, Duration.ofMinutes(1));
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("claim_expires_at", now.minusSeconds(1))
            .in("id", retryable.id, terminal.id));

        int recovered = claimService.recoverExpiredClaims(now);

        assertThat(recovered).isEqualTo(2);
        assertThat(taskMapper.selectById(retryable.id).status).isEqualTo(AiExecutionStatus.PENDING.name());
        assertThat(taskMapper.selectById(terminal.id).status).isEqualTo(AiExecutionStatus.TIMED_OUT.name());
    }

    @Test
    void regenerationCreatesANewExecutionVersionAndPreservesOriginal() {
        AiExecutionTaskEntity original = executionService.create(command(8501L, "original-version", false));
        LocalDateTime now = LocalDateTime.now();
        AiExecutionClaim claim = claimService.claim(original.id, "worker", now, Duration.ofMinutes(5));
        claimService.markSucceeded(
            original.id,
            claim.attemptId(),
            "worker",
            "SCRIPT_VERSION",
            8502L,
            now.plusSeconds(1)
        );

        AiExecutionTaskEntity regenerated = executionService.regenerate(
            original.id,
            "regenerated-version",
            "trace-regenerated-version"
        );

        assertThat(regenerated.id).isNotEqualTo(original.id);
        assertThat(regenerated.executionVersion).isEqualTo(2);
        assertThat(regenerated.status).isEqualTo(AiExecutionStatus.PENDING.name());
        assertThat(taskMapper.selectById(original.id).resultId).isEqualTo(8502L);
    }

    private AiExecutionCreateCommand command(Long tenantId, String key, boolean retryable) {
        return new AiExecutionCreateCommand(
            tenantId,
            tenantId + 1,
            tenantId + 2,
            "TEST_SCENE",
            "TEXT_GENERATION",
            "TEST_RESOURCE",
            tenantId + 3,
            null,
            "SUBMIT",
            key,
            "trace-" + key,
            retryable,
            null
        );
    }
}
