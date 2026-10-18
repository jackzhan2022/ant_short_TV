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
import java.util.HashMap;
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

    private final Map<String, Long> pointVersions = new HashMap<>();

    @Autowired
    private AiPointSettlementService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired private PointAccountingService accounting;

    @Autowired
    private AiExecutionService executionService;

    @Autowired
    private AiExecutionTaskMapper executionTaskMapper;

    @BeforeEach
    void clean() {
        jdbc.update("delete from point_ledger");
        jdbc.update("delete from ai_point_reservation");
        jdbc.update("delete from ai_model_point_price_component");
        jdbc.update("delete from ai_model_point_price_version");
        jdbc.update("delete from ai_model_price_component");
        jdbc.update("delete from ai_model_price_version");
        jdbc.update("delete from ai_model_price_version_sequence");
        jdbc.update("delete from ai_point_policy_component");
        jdbc.update("delete from ai_point_policy_version");
        jdbc.update("delete from team_point_account");
        pointVersions.clear();
    }

    @Test
    void trialPointsSupportAllNonVideoGenerationModelsAndStayRestrictedAfterRefund() {
        int index = 0;
        for (String type : java.util.List.of("TEXT", "IMAGE", "VIDEO_UNDERSTANDING", "AUDIO", "VOICE")) {
            long tenantId = 880L + index++;
            String scene = "trial_" + type;
            account(tenantId, "100");
            accounting.grantTrial(tenantId, 2L);
            fixedPolicy(scene, "1000");
            trialModel(scene, type);
            AiPointReservationEntity reservation = service.reserve(new AiPointReservationCommand(
                tenantId, 2L, tenantId, 1, scene, "TEST", tenantId, modelId(scene), type,
                Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of(), pointVersions.get(scene), scene));
            assertThat(reservation.trialReservedPoints).isEqualByComparingTo("1000");
            service.settle(reservation.id, Map.of(AiUsageMetric.CALL, new BigDecimal("3.05")), 1L, 1L, scene + ":settle");
            assertThat(accountValue(tenantId, "trial_balance")).isZero();
            assertThat(accountValue(tenantId, "balance")).isEqualByComparingTo("50");
            service.refund(reservation.id, scene + ":refund");
            service.refund(reservation.id, scene + ":refund");
            assertThat(accountValue(tenantId, "trial_balance")).isEqualByComparingTo("3000");
            assertThat(accountValue(tenantId, "balance")).isEqualByComparingTo("3100");
            assertThat(service.reconcile(tenantId).matches()).isTrue();
        }
    }

    @Test
    void videoGenerationCannotReserveOrIncrementUsingTrialPointsEvenWithWrongCapability() {
        account(890L, "100");
        accounting.grantTrial(890L, 2L);
        fixedPolicy("trial_video", "60");
        trialModel("trial_video", "VIDEO");
        var command = new AiPointReservationCommand(890L, 2L, 890L, 1, "trial_video", "AI_VIDEO_TASK", 890L,
            modelId("trial_video"), "TEXT", Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of(),
            pointVersions.get("trial_video"), "trial-video");
        var reservation = service.reserve(command);
        assertThat(reservation.trialEligible).isFalse();
        assertThatThrownBy(() -> service.authorizeUsage(reservation.id, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), "video-extra"))
            .isInstanceOf(BusinessException.class);
        var result = service.settle(reservation.id, Map.of(AiUsageMetric.CALL, new BigDecimal("2")), 1L, 1L, "video-overage");
        assertThat(result.status).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
        assertThat(accountValue(890L, "trial_balance")).isEqualByComparingTo("3000");
        service.release(reservation.id, "video-release");
        assertThat(accountValue(890L, "balance")).isEqualByComparingTo("3100");
        jdbc.update("update team_point_account set balance=trial_balance where tenant_id=890");
        assertThatThrownBy(() -> service.reserveRetry(reservation.id, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), "video-retry"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentRequestsCannotSpendTheSameTrialPoints() throws Exception {
        account(893L, "100");
        accounting.grantTrial(893L, 2L);
        fixedPolicy("script_generate", "2000");
        trialModel("script_generate", "TEXT");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> reserveAfter(start, command(893L, 8931L, "trial-race-a")));
            Future<Object> second = pool.submit(() -> reserveAfter(start, command(893L, 8932L, "trial-race-b")));
            start.countDown();
            var results = java.util.List.of(first.get(), second.get());
            assertThat(results.stream().filter(AiPointReservationEntity.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
            assertThat(accountValue(893L, "balance")).isEqualByComparingTo("1100");
            assertThat(accountValue(893L, "trial_balance")).isEqualByComparingTo("1000");
            var reserved = (AiPointReservationEntity) results.stream().filter(AiPointReservationEntity.class::isInstance).findFirst().orElseThrow();
            service.release(reserved.id, "trial-race-release");
            assertThat(accountValue(893L, "trial_balance")).isEqualByComparingTo("3000");
            assertThat(service.reconcile(893L).matches()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void trialReleaseRetryAndCumulativeAuthorizationPreserveOriginalPool() {
        account(891L, "100");
        accounting.grantTrial(891L, 2L);
        fixedPolicy("script_generate", "1000");
        trialModel("script_generate", "TEXT");
        var reservation = service.reserve(command(891L, 8911L, "trial-cumulative"));
        service.release(reservation.id, "trial-cancel");
        assertThat(accountValue(891L, "trial_balance")).isEqualByComparingTo("3000");
        service.reserveRetry(reservation.id, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), "trial-retry");
        service.settleCumulative(reservation.id, Map.of(AiUsageMetric.CALL, new BigDecimal("0.5")), 1L, 1L, "trial-first");
        service.authorizeUsage(reservation.id, Map.of(AiUsageMetric.CALL, new BigDecimal("2")), "trial-next");
        service.authorizeUsage(reservation.id, Map.of(AiUsageMetric.CALL, new BigDecimal("2")), "trial-next");
        service.settleCumulative(reservation.id, Map.of(AiUsageMetric.CALL, new BigDecimal("2.5")), 2L, 2L, "trial-second");
        assertThat(accountValue(891L, "trial_balance")).isEqualByComparingTo("500");
        assertThat(accountValue(891L, "balance")).isEqualByComparingTo("600");
        service.refund(reservation.id, "trial-all-refund");
        assertThat(accountValue(891L, "trial_balance")).isEqualByComparingTo("3000");
        assertThat(service.reconcile(891L).matches()).isTrue();
    }

    private void trialModel(String scene, String type) {
        jdbc.update("delete from ai_model where id=?", modelId(scene));
        jdbc.update("""
            insert into ai_model (id,provider_id,code,name,model_code,service_type,status,is_default,sort,created_at,updated_at)
            values (?,0,?,?,?,?,'ENABLED',false,0,now(),now())
            """, modelId(scene), scene, scene, scene, type);
    }

    @Test
    void choosesMostSpecificComponentInsteadOfAddingFallbackPrice() {
        usagePolicy("dimension_price", AiUsageMetric.IMAGE, "1", "10");
        Long version = pointVersions.get("dimension_price");
        jdbc.update("""
            insert into ai_model_point_price_component
              (price_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
            values (?, 'IMAGE', 1, 20, '{"quality":"HIGH"}', 'quality=HIGH', now())
            """, version);
        assertThat(service.calculateModelPoints(version, Map.of(AiUsageMetric.IMAGE, new BigDecimal("2")),
            Map.of("quality", "HIGH"))).isEqualByComparingTo("40");
    }

    @Test
    void missingChargeableUsageRemainsUnderReviewWithoutReleasingFunds() {
        account(291L, "100");
        usagePolicy("missing_image_usage", AiUsageMetric.IMAGE, "1", "10");
        AiPointReservationEntity reservation = service.reserve(new AiPointReservationCommand(
            291L, 2L, 2911L, 1, "missing_image_usage", "AI_IMAGE_TASK", 2911L,
            modelId("missing_image_usage"), "IMAGE", Map.of(AiUsageMetric.IMAGE, BigDecimal.ONE),
            Map.of(), pointVersions.get("missing_image_usage"), "reserve-2911"));
        AiPointReservationEntity result = service.settle(reservation.id, Map.of(), 1L, 1L, "missing-2911");
        assertThat(result.status).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
        assertThat(result.settledPoints).isZero();
        assertThat(accountValue(291L, "reserved_balance")).isEqualByComparingTo("10");
        assertThat(service.reconcile(291L).matches()).isTrue();
    }

    @Test
    void authorizesAdditionalTokensOnceAndRejectsOverBudgetBeforeCallingProvider() {
        account(292L, "100");
        usagePolicy("token_budget", AiUsageMetric.OUTPUT_TOKEN, "1000", "10");
        AiPointReservationEntity reservation = service.reserve(new AiPointReservationCommand(
            292L, 2L, 2921L, 1, "token_budget", "SCRIPT_OPERATION", 2921L,
            modelId("token_budget"), "TEXT", Map.of(AiUsageMetric.OUTPUT_TOKEN, BigDecimal.ZERO),
            Map.of(), pointVersions.get("token_budget"), "reserve-2921"));
        assertThat(service.usesTokenPricing(reservation.id)).isTrue();
        service.authorizeUsage(reservation.id, Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("2000")), "call-a");
        service.authorizeUsage(reservation.id, Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("2000")), "call-a");
        assertThat(accountValue(292L, "reserved_balance")).isEqualByComparingTo("20");
        assertThatThrownBy(() -> service.authorizeUsage(reservation.id,
            Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("9000")), "call-b"))
            .isInstanceOf(BusinessException.class);
        assertThat(accountValue(292L, "balance")).isEqualByComparingTo("80");
        assertThat(service.reconcile(292L).matches()).isTrue();
    }

    @Test
    void cumulativeRetrySettlementChargesOnlyNewUsage() {
        account(293L, "25");
        usagePolicy("cumulative_tokens", AiUsageMetric.OUTPUT_TOKEN, "1000", "10");
        AiPointReservationEntity reservation = service.reserve(new AiPointReservationCommand(
            293L, 2L, 2931L, 1, "cumulative_tokens", "SCRIPT_OPERATION", 2931L,
            modelId("cumulative_tokens"), "TEXT", Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("2000")),
            Map.of(), pointVersions.get("cumulative_tokens"), "reserve-2931"));
        service.settleCumulative(reservation.id, Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("1000")),
            1L, 1L, "first");
        service.authorizeUsage(reservation.id, Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("1000")), "retry-call");
        assertThat(accountValue(293L, "reserved_balance")).isEqualByComparingTo("10");
        AiPointReservationEntity result = service.settleCumulative(reservation.id,
            Map.of(AiUsageMetric.OUTPUT_TOKEN, new BigDecimal("1500")), 2L, 2L, "retry-settle");
        assertThat(result.settledPoints).isEqualByComparingTo("15");
        assertThat(accountValue(293L, "balance")).isEqualByComparingTo("10");
        assertThat(accountValue(293L, "reserved_balance")).isZero();
        assertThat(service.reconcile(293L).matches()).isTrue();
    }

    @Test
    void rejectsNewReservationsWithoutFrozenModelPointPrice() {
        account(200L, "10");
        AiPointReservationCommand legacy = new AiPointReservationCommand(
            200L, 2L, 2001L, 1, "script_generate", "SCRIPT_OPERATION", 2001L,
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of(), "legacy-reserve"
        );

        assertThatThrownBy(() -> service.reserve(legacy))
            .isInstanceOf(com.antshorttv.accounting.ModelBillingMissingException.class);
        assertThat(reservationCount(200L)).isZero();
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
            modelId("video_generate"), "VIDEO",
            Map.of(AiUsageMetric.VIDEO_SECOND, new BigDecimal("5")), Map.of(),
            pointVersions.get("video_generate"), "reserve-2101"
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
            "select count(*) from point_ledger where reservation_id = ? and entry_type = 'SETTLE'",
            Integer.class,
            reservation.id
        )).isEqualTo(1);
        Map<String, Object> ledger = jdbc.queryForMap("""
            select execution_id, execution_version, business_type, business_id,
                   attempt_id, ai_call_log_id, policy_version_id, idempotency_key
              from point_ledger
             where reservation_id = ? and entry_type = 'SETTLE'
            """, reservation.id);
        assertThat(ledger.get("execution_id")).isEqualTo(2101L);
        assertThat(ledger.get("business_type")).isEqualTo("AI_VIDEO_TASK");
        assertThat(ledger.get("attempt_id")).isEqualTo(3101L);
        assertThat(ledger.get("ai_call_log_id")).isEqualTo(4101L);
        assertThat(ledger.get("policy_version_id")).isNull();
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
        service.settle(settled.id, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), 3202L, 4202L, "settle-2202");
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
            modelId("image_generate"), "IMAGE",
            Map.of(AiUsageMetric.IMAGE, new BigDecimal("2")), Map.of(),
            pointVersions.get("image_generate"), "reserve-2301"
        ));
        AiPointReservationEntity settled = service.settle(
            enough.id, Map.of(AiUsageMetric.IMAGE, new BigDecimal("4")), 3301L, 4301L, "settle-2301"
        );
        assertThat(settled.status).isEqualTo("SETTLED");
        assertThat(accountValue(204L, "balance")).isEqualByComparingTo("1");

        AiPointReservationEntity insufficient = service.reserve(new AiPointReservationCommand(
            204L, 2L, 2302L, 1, "image_generate", "AI_IMAGE_TASK", 94L,
            modelId("image_generate"), "IMAGE",
            Map.of(AiUsageMetric.IMAGE, BigDecimal.ONE), Map.of(),
            pointVersions.get("image_generate"), "reserve-2302"
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
        modelBilling(9001L, "2");
        AiExecutionCreateCommand insufficient = executionCommand(206L, "atomic-insufficient");

        assertThatThrownBy(() -> executionService.createWithReservation(
            insufficient, Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of()
        ))
            .isInstanceOf(BusinessException.class);
        assertThat(executionTaskMapper.selectCount(new QueryWrapper<AiExecutionTaskEntity>()
            .eq("tenant_id", 206L)
            .eq("client_idempotency_key", "atomic-insufficient"))).isZero();

        account(207L, "3");
        AiExecutionTaskEntity task = executionService.createWithReservation(
            executionCommand(207L, "atomic-success"),
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of()
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
    void freezesActiveMembershipDiscountWhenCreatingExecution() {
        account(209L, "10");
        modelBilling(9001L, "2");
        activeCommercialDiscount(209L, "0.50");

        AiExecutionTaskEntity task = executionService.createWithReservation(
            executionCommand(209L, "discounted-execution"),
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of());

        assertThat(task.reservedPoints).isEqualByComparingTo("1.00000000");
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
            businessFailure.id, AiSettlementOutcome.BUSINESS_FAILURE,
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE), 3503L, 4503L, "outcome-2503"
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
            modelId("script_generate"), "TEXT",
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of(),
            pointVersions.get("script_generate"), key
        );
    }

    private AiExecutionCreateCommand executionCommand(Long tenantId, String key) {
        return new AiExecutionCreateCommand(
            tenantId, 2L, 3L, "script_generate", "TEXT", "SCRIPT_OPERATION", 4L,
            9001L, "SUBMIT", key, "trace-" + key, false, null
        );
    }

    private void modelBilling(Long modelId, String points) {
        jdbc.update("""
            insert into ai_model_price_version
              (model_id, version_no, status, effective_from, published_at, created_at)
            values (?, 1, 'PUBLISHED', dateadd('hour', -1, now()), now(), now())
            """, modelId);
        Long costVersionId = jdbc.queryForObject(
            "select max(id) from ai_model_price_version where model_id = ?", Long.class, modelId
        );
        jdbc.update("""
            insert into ai_model_price_component
              (price_version_id, metric, unit_size, unit_price, currency,
               dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, 0.1, 'USD', '{}', '', now())
            """, costVersionId);

        jdbc.update("""
            insert into ai_model_point_price_version
              (model_id, version_no, status, effective_from, published_at, created_at)
            values (?, 1, 'PUBLISHED', dateadd('hour', -1, now()), now(), now())
            """, modelId);
        Long pointVersionId = jdbc.queryForObject(
            "select max(id) from ai_model_point_price_version where model_id = ?", Long.class, modelId
        );
        jdbc.update("""
            insert into ai_model_point_price_component
              (price_version_id, metric, unit_size, point_rate,
               dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, ?, '{}', '', now())
            """, pointVersionId, new BigDecimal(points));
    }

    private void account(Long tenantId, String balance) {
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, reserved_balance, total_granted, total_consumed,
               total_reserved, total_released, total_refunded, version, created_at, updated_at)
            values (?, ?, 0, ?, 0, 0, 0, 0, 0, now(), now())
            """, tenantId, new BigDecimal(balance), new BigDecimal(balance));
    }

    private void activeCommercialDiscount(Long tenantId, String rate) {
        jdbc.update("""
            insert into commercial_package
              (code, package_type, status, created_at, updated_at)
            values (?, 'SUBSCRIPTION', 'ACTIVE', now(), now())
            """, "DISCOUNT_" + tenantId);
        Long packageId = jdbc.queryForObject(
            "select max(id) from commercial_package where code=?", Long.class, "DISCOUNT_" + tenantId);
        jdbc.update("""
            insert into commercial_package_version
              (package_id, version_no, name, billing_period, period_months, price, currency,
               effective_from, status, created_at)
            values (?, 1, 'Discount', 'MONTH', 1, 1, 'CNY', dateadd('day', -1, now()), 'PUBLISHED', now())
            """, packageId);
        Long versionId = jdbc.queryForObject(
            "select max(id) from commercial_package_version where package_id=?", Long.class, packageId);
        jdbc.update("""
            insert into commercial_entitlement
              (package_version_id, entitlement_type, numeric_value, created_at)
            values (?, 'GLOBAL_DISCOUNT', ?, now())
            """, versionId, new BigDecimal(rate));
        jdbc.update("""
            insert into team_subscription
              (tenant_id, package_version_id, source_order_id, status, starts_at, ends_at,
               snapshot_json, created_at, updated_at)
            values (?, ?, 999999, 'ACTIVE', dateadd('day', -1, now()), dateadd('day', 1, now()),
                    '{}', now(), now())
            """, tenantId, versionId);
    }

    private void fixedPolicy(String scene, String points) {
        Long priceVersionId = pointPrice(scene);
        jdbc.update("""
            insert into ai_model_point_price_component
              (price_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, ?, '{}', '', now())
            """, priceVersionId, new BigDecimal(points));
    }

    private void usagePolicy(String scene, AiUsageMetric metric, String unitSize, String rate) {
        Long priceVersionId = pointPrice(scene);
        jdbc.update("""
            insert into ai_model_point_price_component
              (price_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
            values (?, ?, ?, ?, '{}', '', now())
            """, priceVersionId, metric.name(), new BigDecimal(unitSize), new BigDecimal(rate));
    }

    private Long pointPrice(String scene) {
        jdbc.update("""
            insert into ai_model_point_price_version
              (model_id, version_no, status, effective_from, created_at, published_at)
            values (?, 1, 'PUBLISHED', ?, now(), now())
            """, modelId(scene), LocalDateTime.of(2026, 1, 1, 0, 0));
        Long id = jdbc.queryForObject(
            "select id from ai_model_point_price_version where model_id = ?",
            Long.class, modelId(scene));
        pointVersions.put(scene, id);
        return id;
    }

    private Long modelId(String scene) {
        return 700_000L + Integer.toUnsignedLong(scene.hashCode());
    }

    private BigDecimal accountValue(Long tenantId, String column) {
        return jdbc.queryForObject(
            "select " + column + " from team_point_account where tenant_id = ?",
            BigDecimal.class,
            tenantId
        );
    }

    private int reservationCount(Long tenantId) {
        return jdbc.queryForObject(
            "select count(*) from ai_point_reservation where tenant_id = ?", Integer.class, tenantId);
    }
}
