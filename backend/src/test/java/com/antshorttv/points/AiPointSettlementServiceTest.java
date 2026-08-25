package com.antshorttv.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiPointSettlementServiceTest {

    @Autowired
    private AiPointSettlementService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiExecutionService executionService;

    @Autowired
    private AiExecutionTaskMapper executionTaskMapper;

    @BeforeEach
    void clean() {
        jdbc.update("delete from ai_point_ledger");
        jdbc.update("delete from ai_point_reservation");
        jdbc.update("delete from ai_point_policy_component");
        jdbc.update("delete from ai_point_policy_version");
        jdbc.update("delete from team_point_account");
    }

    @Test
    void reservesIdempotentlyAndRejectsConcurrentOverReservation() throws Exception {
        account(201L, "10");
        fixedPolicy("script_generate", "7");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> reserveAfter(start, command(201L, 2001L, "reserve-2001")));
            Future<Object> second = pool.submit(() -> reserveAfter(start, command(201L, 2002L, "reserve-2002")));
            start.countDown();

            Object firstResult = first.get();
            Object secondResult = second.get();
            long successCount = java.util.stream.Stream.of(firstResult, secondResult)
                .filter(AiPointReservationEntity.class::isInstance)
                .count();
            assertThat(successCount).isEqualTo(1);
            assertThat(accountValue(201L, "balance")).isEqualByComparingTo("3");
            assertThat(accountValue(201L, "reserved_balance")).isEqualByComparingTo("7");

            AiPointReservationEntity existing = (AiPointReservationEntity) java.util.stream.Stream
                .of(firstResult, secondResult)
                .filter(AiPointReservationEntity.class::isInstance)
                .findFirst()
                .orElseThrow();
            AiPointReservationEntity duplicate = service.reserve(command(
                201L, existing.executionId, existing.idempotencyKey
            ));
            assertThat(duplicate.id).isEqualTo(existing.id);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void settlesExactlyOnceAndReleasesUnusedReservation() {
        account(202L, "10");
        usagePolicy("video_generate", AiUsageMetric.VIDEO_SECOND, "1", "1");
        AiPointReservationEntity reservation = service.reserve(new AiPointReservationCommand(
            202L, 2L, 2101L, 1, "video_generate", "AI_VIDEO_TASK", 91L,
            Map.of(AiUsageMetric.VIDEO_SECOND, new BigDecimal("5")), Map.of(), "reserve-2101"
        ));

        AiPointReservationEntity settled = service.settle(
            reservation.id,
            Map.of(AiUsageMetric.VIDEO_SECOND, new BigDecimal("3")),
            3101L,
            4101L,
            "settle-2101"
        );
        AiPointReservationEntity duplicate = service.settle(
            reservation.id,
            Map.of(AiUsageMetric.VIDEO_SECOND, new BigDecimal("3")),
            3101L,
            4101L,
            "settle-2101"
        );

        assertThat(settled.status).isEqualTo("SETTLED");
        assertThat(duplicate.settledPoints).isEqualByComparingTo("3");
        assertThat(accountValue(202L, "balance")).isEqualByComparingTo("7");
        assertThat(accountValue(202L, "reserved_balance")).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_point_ledger where reservation_id = ? and entry_type = 'SETTLE'",
            Integer.class,
            reservation.id
        )).isEqualTo(1);
        Map<String, Object> ledger = jdbc.queryForMap("""
            select execution_id, execution_version, business_type, business_id,
                   attempt_id, ai_call_log_id, policy_version_id, idempotency_key
              from ai_point_ledger
             where reservation_id = ? and entry_type = 'SETTLE'
            """, reservation.id);
        assertThat(ledger.get("execution_id")).isEqualTo(2101L);
        assertThat(ledger.get("business_type")).isEqualTo("AI_VIDEO_TASK");
        assertThat(ledger.get("attempt_id")).isEqualTo(3101L);
        assertThat(ledger.get("ai_call_log_id")).isEqualTo(4101L);
        assertThat(ledger.get("policy_version_id")).isNotNull();
        assertThat(ledger.get("idempotency_key")).isEqualTo("settle-2101:settle");
    }

    @Test
    void releasesBeforeCallAndRefundsSettledCharge() {
        account(203L, "10");
        fixedPolicy("script_generate", "2");
        AiPointReservationEntity released = service.reserve(command(203L, 2201L, "reserve-2201"));
        service.release(released.id, "release-2201");
        assertThat(accountValue(203L, "balance")).isEqualByComparingTo("10");

        AiPointReservationEntity settled = service.reserve(command(203L, 2202L, "reserve-2202"));
        service.settle(settled.id, Map.of(), 3202L, 4202L, "settle-2202");
        service.refund(settled.id, "refund-2202");

        assertThat(accountValue(203L, "balance")).isEqualByComparingTo("10");
        assertThat(service.reconcile(203L).matches()).isTrue();
    }

    @Test
    void incrementallyReservesOverageOrMarksSettlementReviewWithoutNegativeBalance() {
        account(204L, "5");
        usagePolicy("image_generate", AiUsageMetric.IMAGE, "1", "1");
        AiPointReservationEntity enough = service.reserve(new AiPointReservationCommand(
            204L, 2L, 2301L, 1, "image_generate", "AI_IMAGE_TASK", 93L,
            Map.of(AiUsageMetric.IMAGE, new BigDecimal("2")), Map.of(), "reserve-2301"
        ));
        AiPointReservationEntity settled = service.settle(
            enough.id, Map.of(AiUsageMetric.IMAGE, new BigDecimal("4")), 3301L, 4301L, "settle-2301"
        );
        assertThat(settled.status).isEqualTo("SETTLED");
        assertThat(accountValue(204L, "balance")).isEqualByComparingTo("1");

        AiPointReservationEntity insufficient = service.reserve(new AiPointReservationCommand(
            204L, 2L, 2302L, 1, "image_generate", "AI_IMAGE_TASK", 94L,
            Map.of(AiUsageMetric.IMAGE, BigDecimal.ONE), Map.of(), "reserve-2302"
        ));
        AiPointReservationEntity review = service.settle(
            insufficient.id, Map.of(AiUsageMetric.IMAGE, new BigDecimal("3")), 3302L, 4302L, "settle-2302"
        );

        assertThat(review.status).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
        assertThat(accountValue(204L, "balance")).isZero();
        assertThat(accountValue(204L, "reserved_balance")).isEqualByComparingTo("1");
    }

    @Test
    void rejectsInsufficientBalanceBeforeCreatingReservation() {
        account(205L, "1");
        fixedPolicy("script_generate", "2");

        assertThatThrownBy(() -> service.reserve(command(205L, 2401L, "reserve-2401")))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_POINTS_INSUFFICIENT));
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_point_reservation where tenant_id = 205",
            Integer.class
        )).isZero();
    }

    @Test
    void createsExecutionAndReservationAtomically() {
        account(206L, "1");
        fixedPolicy("script_generate", "2");
        AiExecutionCreateCommand insufficient = executionCommand(206L, "atomic-insufficient");

        assertThatThrownBy(() -> executionService.createWithReservation(insufficient, Map.of(), Map.of()))
            .isInstanceOf(BusinessException.class);
        assertThat(executionTaskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", 206L)
            .eq("client_idempotency_key", "atomic-insufficient"))).isZero();

        account(207L, "3");
        jdbc.update("update ai_point_policy_version set scene = 'unused' where scene = 'script_generate'");
        fixedPolicy("script_generate", "2");
        AiExecutionTaskEntity task = executionService.createWithReservation(
            executionCommand(207L, "atomic-success"), Map.of(), Map.of()
        );

        assertThat(task.reservedPoints).isEqualByComparingTo("2");
        assertThat(task.pointSettlementStatus).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_point_reservation where execution_id = ?",
            Integer.class,
            task.id
        )).isEqualTo(1);
    }

    @Test
    void appliesVersionedFailureSettlementPolicy() {
        account(208L, "10");
        fixedPolicy("script_generate", "2");

        AiPointReservationEntity canceled = service.reserve(command(208L, 2501L, "reserve-2501"));
        assertThat(service.finalizeOutcome(
            canceled.id, AiSettlementOutcome.PRE_CALL_CANCELED, Map.of(), null, null, "outcome-2501"
        ).status).isEqualTo("RELEASED");

        AiPointReservationEntity rejected = service.reserve(command(208L, 2502L, "reserve-2502"));
        assertThat(service.finalizeOutcome(
            rejected.id, AiSettlementOutcome.PROVIDER_REJECTION, Map.of(), 3502L, 4502L, "outcome-2502"
        ).status).isEqualTo("RELEASED");

        AiPointReservationEntity businessFailure = service.reserve(command(208L, 2503L, "reserve-2503"));
        assertThat(service.finalizeOutcome(
            businessFailure.id, AiSettlementOutcome.BUSINESS_FAILURE, Map.of(), 3503L, 4503L, "outcome-2503"
        ).status).isEqualTo("SETTLED");
        assertThat(accountValue(208L, "balance")).isEqualByComparingTo("8");
    }

    private Object reserveAfter(CountDownLatch start, AiPointReservationCommand command) {
        try {
            start.await();
            return service.reserve(command);
        } catch (Exception exception) {
            return exception;
        }
    }

    private AiPointReservationCommand command(Long tenantId, Long executionId, String key) {
        return new AiPointReservationCommand(
            tenantId, 2L, executionId, 1, "script_generate", "SCRIPT_OPERATION", executionId,
            Map.of(), Map.of(), key
        );
    }

    private AiExecutionCreateCommand executionCommand(Long tenantId, String key) {
        return new AiExecutionCreateCommand(
            tenantId, 2L, 3L, "script_generate", "TEXT", "SCRIPT_OPERATION", 4L,
            null, "SUBMIT", key, "trace-" + key, false, null
        );
    }

    private void account(Long tenantId, String balance) {
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, reserved_balance, total_granted, total_consumed,
               total_reserved, total_released, total_refunded, version, created_at, updated_at)
            values (?, ?, 0, ?, 0, 0, 0, 0, 0, now(), now())
            """, tenantId, new BigDecimal(balance), new BigDecimal(balance));
    }

    private void fixedPolicy(String scene, String points) {
        Long policyId = policy(scene);
        jdbc.update("""
            insert into ai_point_policy_component
              (policy_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
            values (?, 'FIXED_EXECUTION', 1, ?, '{}', '', now())
            """, policyId, new BigDecimal(points));
    }

    private void usagePolicy(String scene, AiUsageMetric metric, String unitSize, String rate) {
        Long policyId = policy(scene);
        jdbc.update("""
            insert into ai_point_policy_component
              (policy_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
            values (?, ?, ?, ?, '{}', '', now())
            """, policyId, metric.name(), new BigDecimal(unitSize), new BigDecimal(rate));
    }

    private Long policy(String scene) {
        jdbc.update("""
            insert into ai_point_policy_version
              (scene, version_no, status, effective_from, charge_provider_rejection,
               charge_provider_billed_failure, charge_timeout, charge_business_failure,
               created_at, published_at)
            values (?, 1, 'PUBLISHED', ?, false, true, true, true, now(), now())
            """, scene, LocalDateTime.of(2026, 1, 1, 0, 0));
        return jdbc.queryForObject(
            "select id from ai_point_policy_version where scene = ?",
            Long.class,
            scene
        );
    }

    private BigDecimal accountValue(Long tenantId, String column) {
        return jdbc.queryForObject(
            "select " + column + " from team_point_account where tenant_id = ?",
            BigDecimal.class,
            tenantId
        );
    }
}
