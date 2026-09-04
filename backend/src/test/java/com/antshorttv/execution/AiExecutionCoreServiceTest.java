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
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbc;

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
    void incompleteOrRevokedBillingRejectsBeforeAnyExecutionArtifacts() {
        long tenantId = 8611L;
        long modelId = 999911L;
        account(tenantId, "20");
        Long costVersionId = costPrice(modelId, 1, "PUBLISHED", null);

        assertPreflightRejected(tenantId, modelId, "missing-point-price");

        Long pointVersionId = pointPrice(modelId, 1, "PUBLISHED", null, "2");
        jdbc.update("update ai_model_price_version set status = 'REVOKED' where id = ?", costVersionId);
        assertPreflightRejected(tenantId, modelId, "revoked-cost-price");

        jdbc.update("update ai_model_price_version set status = 'PUBLISHED' where id = ?", costVersionId);
        jdbc.update("update ai_model_point_price_version set status = 'REVOKED' where id = ?", pointVersionId);
        assertPreflightRejected(tenantId, modelId, "revoked-point-price");

        jdbc.update("update ai_model_point_price_version set status = 'PUBLISHED' where id = ?", pointVersionId);
        assertThatThrownBy(() -> executionService.createWithReservation(
            billingCommand(tenantId, modelId, "unsupported-metric"),
            Map.of(AiUsageMetric.IMAGE, BigDecimal.ONE),
            Map.of()
        )).isInstanceOf(ModelBillingMissingException.class);
        assertThat(taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", tenantId).eq("client_idempotency_key", "unsupported-metric"))).isZero();

        assertThat(jdbc.queryForObject(
            "select count(*) from ai_call_log where execution_id in (select id from ai_execution_task where tenant_id = ?)",
            Integer.class,
            tenantId
        )).isZero();
    }

    @Test
    void idempotentReplayAndRetryPreserveFrozenPricesWhileRegenerationResolvesCurrentVersions() {
        long tenantId = 8612L;
        long modelId = 999912L;
        account(tenantId, "20");
        Long originalCostId = costPrice(modelId, 1, "PUBLISHED", null);
        Long originalPointId = pointPrice(modelId, 1, "PUBLISHED", null, "2");
        AiExecutionCreateCommand command = billingCommand(tenantId, modelId, "frozen-billing-original");

        AiExecutionTaskEntity original = executionService.createWithReservation(
            command, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of()
        );
        jdbc.update("update ai_model_point_price_version set status = 'REVOKED' where id = ?", originalPointId);

        AiExecutionTaskEntity replay = executionService.createWithReservation(
            command, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of()
        );
        assertThat(replay.id).isEqualTo(original.id);
        assertThat(replay.costPriceVersionId).isEqualTo(originalCostId);
        assertThat(replay.pointPriceVersionId).isEqualTo(originalPointId);
        assertThat(reservationMapper.selectCount(new QueryWrapper<AiPointReservationEntity>()
            .eq("execution_id", original.id))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from point_ledger where execution_id = ? and entry_type = 'RESERVE'",
            Integer.class,
            original.id
        )).isEqualTo(1);

        jdbc.update("update ai_execution_task set status = 'FAILED' where id = ?", original.id);
        AiExecutionTaskEntity retried = executionService.retry(original.id);
        assertThat(retried.costPriceVersionId).isEqualTo(originalCostId);
        assertThat(retried.pointPriceVersionId).isEqualTo(originalPointId);

        jdbc.update("update ai_execution_task set status = 'SUCCEEDED' where id = ?", original.id);
        LocalDateTime replacementFrom = LocalDateTime.now().minusSeconds(1);
        jdbc.update("update ai_model_price_version set effective_to = ? where id = ?", replacementFrom, originalCostId);
        Long replacementCostId = costPrice(modelId, 2, "PUBLISHED", replacementFrom);
        Long replacementPointId = pointPrice(modelId, 2, "PUBLISHED", replacementFrom, "3");

        AiExecutionTaskEntity regenerated = executionService.regenerateWithReservation(
            original.id,
            original.businessId,
            modelId,
            "frozen-billing-regenerated",
            "trace-frozen-billing-regenerated",
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of()
        );
        assertThat(regenerated.executionVersion).isEqualTo(2);
        assertThat(regenerated.costPriceVersionId).isEqualTo(replacementCostId);
        assertThat(regenerated.pointPriceVersionId).isEqualTo(replacementPointId);
        assertThat(taskMapper.selectById(original.id).costPriceVersionId).isEqualTo(originalCostId);
        assertThat(taskMapper.selectById(original.id).pointPriceVersionId).isEqualTo(originalPointId);
        assertThat(reservationMapper.selectCount(new QueryWrapper<AiPointReservationEntity>()
            .in("execution_id", original.id, regenerated.id))).isEqualTo(2);
    }

    @Test
    void canceledExecutionCanBeRestartedAsANewReservedExecution() {
        long tenantId = 8613L;
        long modelId = 999913L;
        account(tenantId, "20");
        costPrice(modelId, 1, "PUBLISHED", null);
        pointPrice(modelId, 1, "PUBLISHED", null, "2");
        AiExecutionTaskEntity original = executionService.createWithReservation(
            billingCommand(tenantId, modelId, "canceled-analysis-original"),
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of()
        );
        executionService.cancel(original.id);

        AiExecutionTaskEntity restarted = executionService.restartCanceledWithReservation(
            original.id,
            original.businessId,
            modelId,
            "canceled-analysis-restart",
            "trace-canceled-analysis-restart",
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of()
        );

        assertThat(restarted.id).isNotEqualTo(original.id);
        assertThat(restarted.status).isEqualTo("PENDING");
        assertThat(restarted.sourceExecutionId).isEqualTo(original.id);
        assertThat(restarted.rootExecutionId).isEqualTo(original.id);
        assertThat(restarted.executionVersion).isEqualTo(2);
        assertThat(reservationMapper.selectCount(new QueryWrapper<AiPointReservationEntity>()
            .in("execution_id", original.id, restarted.id))).isEqualTo(2);
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
    void cancellationDispositionIsDerivedFromTheAtomicStateTransition() {
        AiExecutionTaskEntity pending = executionService.create(command(8251L, "cancel-pending", false));
        AiExecutionTaskEntity running = executionService.create(command(8252L, "cancel-running", false));
        claimService.claim(running.id, "worker-running", LocalDateTime.now(), Duration.ofMinutes(5));

        var pendingCancellation = executionService.cancelWithDisposition(pending.id);
        var runningCancellation = executionService.cancelWithDisposition(running.id);

        assertThat(pendingCancellation.beforeProviderCall()).isTrue();
        assertThat(runningCancellation.beforeProviderCall()).isFalse();
        assertThat(pendingCancellation.task().status).isEqualTo(AiExecutionStatus.CANCELED.name());
        assertThat(runningCancellation.task().status).isEqualTo(AiExecutionStatus.CANCELED.name());
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

    private void assertPreflightRejected(long tenantId, long modelId, String key) {
        assertThatThrownBy(() -> executionService.createWithReservation(
            billingCommand(tenantId, modelId, key),
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of()
        )).isInstanceOf(ModelBillingMissingException.class);
        assertThat(taskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", tenantId).eq("client_idempotency_key", key))).isZero();
        assertThat(reservationMapper.selectCount(new QueryWrapper<AiPointReservationEntity>()
            .like("idempotency_key", key))).isZero();
    }

    private AiExecutionCreateCommand billingCommand(long tenantId, long modelId, String key) {
        return new AiExecutionCreateCommand(
            tenantId, tenantId + 1, null, "TEST_BILLING", "TEXT", "TEST_RESOURCE", tenantId + 2,
            modelId, "SUBMIT", key, "trace-" + key, true, null
        );
    }

    private void account(long tenantId, String balance) {
        jdbc.update("delete from team_point_account where tenant_id = ?", tenantId);
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, reserved_balance, total_granted, total_consumed,
               total_reserved, total_released, total_refunded, version, created_at, updated_at)
            values (?, ?, 0, ?, 0, 0, 0, 0, 0, now(), now())
            """, tenantId, new BigDecimal(balance), new BigDecimal(balance));
    }

    private Long costPrice(long modelId, int versionNo, String status, LocalDateTime effectiveFrom) {
        jdbc.update("""
            insert into ai_model_price_version
              (model_id, version_no, status, effective_from, published_at, created_at)
            values (?, ?, ?, ?, now(), now())
            """, modelId, versionNo, status,
            effectiveFrom == null ? LocalDateTime.now().minusHours(1) : effectiveFrom);
        Long id = jdbc.queryForObject(
            "select id from ai_model_price_version where model_id = ? and version_no = ?",
            Long.class, modelId, versionNo
        );
        jdbc.update("""
            insert into ai_model_price_component
              (price_version_id, metric, unit_size, unit_price, currency,
               dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, 0.1, 'USD', '{}', '', now())
            """, id);
        return id;
    }

    private Long pointPrice(
        long modelId,
        int versionNo,
        String status,
        LocalDateTime effectiveFrom,
        String rate
    ) {
        jdbc.update("""
            insert into ai_model_point_price_version
              (model_id, version_no, status, effective_from, published_at, created_at)
            values (?, ?, ?, ?, now(), now())
            """, modelId, versionNo, status,
            effectiveFrom == null ? LocalDateTime.now().minusHours(1) : effectiveFrom);
        Long id = jdbc.queryForObject(
            "select id from ai_model_point_price_version where model_id = ? and version_no = ?",
            Long.class, modelId, versionNo
        );
        jdbc.update("""
            insert into ai_model_point_price_component
              (price_version_id, metric, unit_size, point_rate,
               dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, ?, '{}', '', now())
            """, id, new BigDecimal(rate));
        return id;
    }
}
