package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = "commercial.subscription.scheduler.enabled=false")
class CommercialSubscriptionLifecycleTest {
    @Autowired CommercialPackageService packageService;
    @Autowired CommercialOrderService orderService;
    @Autowired CommercialEntitlementOrchestrator orchestrator;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @Autowired com.antshorttv.points.AiPointSettlementService settlementService;
    @Autowired CommercialPaymentLifecycleService paymentLifecycleService;
    @SpyBean com.antshorttv.points.PointAccountingService pointAccountingService;

    @Test
    void failedInitialEntitlementStaysPendingAndCompletesOnRetry() {
        CommercialPackageVersionResponse version = publishedSubscription("RETRY_INITIAL", 1, "100");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(109L, 209L, version.versionId()));
        doThrow(new IllegalStateException("temporary grant failure"))
            .doCallRealMethod()
            .when(pointAccountingService)
            .grant(eq(109L), eq(209L), any(BigDecimal.class), any(String.class), any(String.class));

        CommercialOrderEntity pending = orchestrator.confirmPaid(
            order.id(), "WX-RETRY", new BigDecimal("30.00"), LocalDateTime.of(2026, 8, 26, 20, 0));

        assertThat(pending.status).isEqualTo("ENTITLEMENT_PENDING");
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=109", Integer.class)).isZero();
        assertThat(paymentLifecycleService.retryPendingEntitlements()).isGreaterThanOrEqualTo(1);
        assertThat(orderService.require(order.id()).status).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=109", Integer.class)).isEqualTo(1);
    }

    @Test
    void firstSubscriptionActivatesImmediatelyAndGrantsFirstPeriodOnce() {
        CommercialPackageVersionResponse version = publishedSubscription("FIRST_MONTH", 1, "100");
        LocalDateTime paidAt = LocalDateTime.of(2026, 1, 31, 10, 0);

        CommercialOrderResponse order = pay(101L, 201L, version, paidAt);
        orchestrator.confirmPaid(order.id(), "WX-FIRST-DUP", new BigDecimal("30.00"), paidAt);

        assertThat(jdbc.queryForObject(
            "select status from team_subscription where tenant_id=101", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
            "select starts_at from team_subscription where tenant_id=101", LocalDateTime.class)).isEqualTo(paidAt);
        assertThat(jdbc.queryForObject(
            "select ends_at from team_subscription where tenant_id=101", LocalDateTime.class)).isEqualTo(paidAt.plusMonths(1));
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=101 and period_no=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select balance from team_point_account where tenant_id=101", BigDecimal.class)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject(
            "select count(*) from point_ledger where tenant_id=101 and entry_type='GRANT'", Integer.class)).isEqualTo(1);
        assertThat(settlementService.reconcile(101L).matches()).isTrue();
    }

    @Test
    void samePackageRenewalExtendsTheActiveSubscriptionTail() {
        CommercialPackageVersionResponse version = publishedSubscription("RENEW_QUARTER", 3, "300");
        LocalDateTime firstPaidAt = LocalDateTime.of(2026, 2, 10, 9, 30);
        pay(102L, 202L, version, firstPaidAt);

        pay(102L, 202L, version, firstPaidAt.plusDays(5));

        assertThat(jdbc.queryForObject(
            "select count(*) from team_subscription where tenant_id=102", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select ends_at from team_subscription where tenant_id=102 and status='ACTIVE'", LocalDateTime.class))
            .isEqualTo(firstPaidAt.plusMonths(6));
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=102", Integer.class)).isEqualTo(1);
    }

    @Test
    void subscriptionPurchaseBonusIsGrantedOnceForFirstPurchaseRenewalAndQueuedPurchase() {
        CommercialPackageVersionResponse version = publishedSubscriptionWithBonus("MIXED_BONUS", 1, "100", "40");
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 10, 9, 0);

        CommercialOrderResponse first = pay(110L, 210L, version, paidAt);
        orchestrator.confirmPaid(first.id(), "WX-DUP-BONUS", new BigDecimal("30.00"), paidAt);
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=110", BigDecimal.class))
            .isEqualByComparingTo("140");

        pay(110L, 210L, version, paidAt.plusDays(1));
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=110", BigDecimal.class))
            .isEqualByComparingTo("180");
        assertThat(jdbc.queryForObject("select count(*) from commercial_entitlement_grant where tenant_id=110 and entitlement_type='ONE_TIME_POINTS'", Integer.class))
            .isEqualTo(2);

        CommercialPackageVersionResponse active = publishedSubscription("BEFORE_BONUS_QUEUE", 1, "100");
        pay(111L, 211L, active, paidAt);
        pay(111L, 211L, version, paidAt.plusDays(1));
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=111", BigDecimal.class))
            .isEqualByComparingTo("140");
        assertThat(jdbc.queryForObject("select status from team_subscription where tenant_id=111 and package_version_id=?", String.class, version.versionId()))
            .isEqualTo("QUEUED");
    }

    @Test
    void failedSubscriptionBonusRetriesWithoutDuplicateGrants() {
        CommercialPackageVersionResponse version = publishedSubscriptionWithBonus("RETRY_MIXED_BONUS", 1, "100", "40");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(112L, 212L, version.versionId()));
        doThrow(new IllegalStateException("temporary bonus failure"))
            .doCallRealMethod()
            .when(pointAccountingService)
            .grant(eq(112L), eq(212L), any(BigDecimal.class), any(String.class), any(String.class));

        CommercialOrderEntity pending = orchestrator.confirmPaid(
            order.id(), "WX-RETRY-BONUS", new BigDecimal("30.00"), LocalDateTime.of(2026, 9, 10, 10, 0));
        assertThat(pending.status).isEqualTo("ENTITLEMENT_PENDING");
        assertThat(paymentLifecycleService.retryPendingEntitlements()).isGreaterThanOrEqualTo(1);
        assertThat(paymentLifecycleService.retryPendingEntitlements()).isZero();
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=112", BigDecimal.class))
            .isEqualByComparingTo("140");
        assertThat(jdbc.queryForObject("select count(*) from commercial_entitlement_grant where tenant_id=112", Integer.class))
            .isEqualTo(2);
    }

    @Test
    void zeroValueSubscriptionBonusDoesNotBlockActivation() {
        CommercialPackageVersionResponse version = publishedSubscriptionWithBonus("ZERO_MIXED_BONUS", 1, "100", "0");
        CommercialOrderResponse order = pay(113L, 213L, version, LocalDateTime.of(2026, 9, 11, 10, 0));

        assertThat(orderService.require(order.id()).status).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=113", BigDecimal.class))
            .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from commercial_entitlement_grant where tenant_id=113 and entitlement_type='ONE_TIME_POINTS'", Integer.class))
            .isZero();
    }

    @Test
    void zeroValuePeriodicPointsDoNotBlockLaterSubscriptionPeriods() {
        CommercialPackageVersionResponse version = publishedSubscription("ZERO_PERIODIC_POINTS", 3, "0");
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 12, 10, 0);
        CommercialOrderResponse order = pay(114L, 214L, version, paidAt);

        assertThat(orderService.require(order.id()).status).isEqualTo("COMPLETED");
        processDue(paidAt.plusMonths(1));
        processDue(paidAt.plusMonths(2));
        assertThat(jdbc.queryForObject("select count(*) from commercial_entitlement_grant where tenant_id=114 and status='GRANTED'", Integer.class))
            .isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from point_ledger where tenant_id=114", Integer.class))
            .isZero();
    }

    @Test
    void differentPackagesQueueWithoutOverlapping() {
        CommercialPackageVersionResponse active = publishedSubscription("QUEUE_ACTIVE", 1, "100");
        CommercialPackageVersionResponse next = publishedSubscription("QUEUE_NEXT", 3, "300");
        CommercialPackageVersionResponse last = publishedSubscription("QUEUE_LAST", 6, "600");
        LocalDateTime firstPaidAt = LocalDateTime.of(2026, 3, 15, 8, 0);
        pay(103L, 203L, active, firstPaidAt);

        pay(103L, 203L, next, firstPaidAt.plusDays(1));
        pay(103L, 203L, last, firstPaidAt.plusDays(2));

        List<SubscriptionWindow> queued = jdbc.query(
            "select starts_at, ends_at from team_subscription where tenant_id=103 and status='QUEUED' order by starts_at",
            (rs, rowNum) -> new SubscriptionWindow(
                rs.getTimestamp("starts_at").toLocalDateTime(),
                rs.getTimestamp("ends_at").toLocalDateTime()));
        assertThat(queued).containsExactly(
            new SubscriptionWindow(firstPaidAt.plusMonths(1), firstPaidAt.plusMonths(4)),
            new SubscriptionWindow(firstPaidAt.plusMonths(4), firstPaidAt.plusMonths(10)));
    }

    @Test
    void periodicGrantsKeepTheOriginalMonthlyAnchorAndRemainIdempotent() {
        CommercialPackageVersionResponse version = publishedSubscription("PERIODIC_ANCHOR", 3, "100");
        LocalDateTime paidAt = LocalDateTime.of(2026, 1, 31, 10, 0);
        pay(104L, 204L, version, paidAt);

        processDue(paidAt.plusMonths(1));
        processDue(paidAt.plusMonths(1));

        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=104", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "select next_grant_at from team_subscription where tenant_id=104", LocalDateTime.class))
            .isEqualTo(paidAt.plusMonths(2));
        assertThat(jdbc.queryForObject(
            "select balance from team_point_account where tenant_id=104", BigDecimal.class)).isEqualByComparingTo("200");

        processDue(paidAt.plusMonths(2).minusDays(1));
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=104", Integer.class)).isEqualTo(2);
        processDue(paidAt.plusMonths(2));
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=104", Integer.class)).isEqualTo(3);
    }

    @Test
    void dueQueuedSubscriptionReplacesExpiredSubscriptionAndGetsFirstGrant() {
        CommercialPackageVersionResponse current = publishedSubscription("ACTIVATE_CURRENT", 1, "100");
        CommercialPackageVersionResponse queued = publishedSubscription("ACTIVATE_QUEUED", 1, "250");
        LocalDateTime paidAt = LocalDateTime.of(2026, 5, 15, 12, 0);
        pay(105L, 205L, current, paidAt);
        pay(105L, 205L, queued, paidAt.plusDays(1));

        processDue(paidAt.plusMonths(1));

        assertThat(jdbc.queryForObject(
            "select count(*) from team_subscription where tenant_id=105 and status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from team_subscription where tenant_id=105 and status='EXPIRED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from team_subscription where tenant_id=105 and status='QUEUED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
            "select balance from team_point_account where tenant_id=105", BigDecimal.class)).isEqualByComparingTo("350");
    }

    @Test
    void failedPeriodicGrantIsRecordedAndRetriedWithTheSamePeriod() {
        CommercialPackageVersionResponse version = publishedSubscription("PERIODIC_RETRY", 2, "100");
        LocalDateTime paidAt = LocalDateTime.of(2026, 6, 20, 9, 0);
        pay(106L, 206L, version, paidAt);
        doThrow(new IllegalStateException("temporary periodic grant failure"))
            .doCallRealMethod()
            .when(pointAccountingService)
            .grant(eq(106L), eq(206L), any(BigDecimal.class), any(String.class), any(String.class));

        processDue(paidAt.plusMonths(1));

        assertThat(jdbc.queryForObject(
            "select status from commercial_entitlement_grant where tenant_id=106 and period_no=2", String.class))
            .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
            "select next_grant_at from team_subscription where tenant_id=106", LocalDateTime.class))
            .isEqualTo(paidAt.plusMonths(1));

        processDue(paidAt.plusMonths(1));

        assertThat(jdbc.queryForObject(
            "select status from commercial_entitlement_grant where tenant_id=106 and period_no=2", String.class))
            .isEqualTo("GRANTED");
        assertThat(jdbc.queryForObject(
            "select balance from team_point_account where tenant_id=106", BigDecimal.class)).isEqualByComparingTo("200");
    }

    @Test
    void resolvesOnlyTheCurrentlyActiveGlobalDiscount() {
        CommercialPackageVersionResponse active = publishedSubscription("DISCOUNT_ACTIVE", 1, "100");
        CommercialPackageVersionResponse queued = publishedSubscription("DISCOUNT_QUEUED", 1, "200");
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 1, 0, 0);
        pay(107L, 207L, active, paidAt);
        pay(107L, 207L, queued, paidAt.plusDays(1));

        Object activeSnapshot = resolveDiscount(107L, paidAt.plusDays(15));
        assertThat(ReflectionTestUtils.getField(activeSnapshot, "discountRate"))
            .isEqualTo(new BigDecimal("0.90000000"));
        assertThat(ReflectionTestUtils.getField(activeSnapshot, "subscriptionId")).isNotNull();

        Object expiredSnapshot = resolveDiscount(107L, paidAt.plusMonths(1).plusSeconds(1));
        assertThat(ReflectionTestUtils.getField(expiredSnapshot, "discountRate"))
            .isEqualTo(new BigDecimal("1.00000000"));
        assertThat(ReflectionTestUtils.getField(expiredSnapshot, "subscriptionId")).isNull();

        Object noSubscription = resolveDiscount(9999L, paidAt);
        assertThat(ReflectionTestUtils.getField(noSubscription, "discountRate"))
            .isEqualTo(new BigDecimal("1.00000000"));
    }

    private CommercialPackageVersionResponse publishedSubscription(String code, int periodMonths, String periodicPoints) {
        CommercialPackageVersionResponse draft = packageService.createDraft(new CommercialPackageDraftCommand(
            code, "SUBSCRIPTION", code, null, "MONTH", periodMonths,
            new BigDecimal("30.00"), null, "CNY", LocalDateTime.of(2026, 1, 1, 0, 0), null,
            List.of(
                new CommercialEntitlementInput("PERIODIC_POINTS", new BigDecimal(periodicPoints)),
                new CommercialEntitlementInput("GLOBAL_DISCOUNT", new BigDecimal("0.90"))),
            1L));
        return packageService.publish(draft.packageId(), draft.versionId(), 1L);
    }

    private CommercialPackageVersionResponse publishedSubscriptionWithBonus(
        String code, int periodMonths, String periodicPoints, String oneTimePoints
    ) {
        CommercialPackageVersionResponse draft = packageService.createDraft(new CommercialPackageDraftCommand(
            code, "SUBSCRIPTION", code, null, "MONTH", periodMonths,
            new BigDecimal("30.00"), null, "CNY", LocalDateTime.of(2026, 1, 1, 0, 0), null,
            List.of(
                new CommercialEntitlementInput("PERIODIC_POINTS", new BigDecimal(periodicPoints)),
                new CommercialEntitlementInput("ONE_TIME_POINTS", new BigDecimal(oneTimePoints))),
            1L));
        return packageService.publish(draft.packageId(), draft.versionId(), 1L);
    }

    private CommercialOrderResponse pay(
        Long tenantId,
        Long userId,
        CommercialPackageVersionResponse version,
        LocalDateTime paidAt
    ) {
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(tenantId, userId, version.versionId()));
        orchestrator.confirmPaid(order.id(), "WX-" + order.id(), new BigDecimal("30.00"), paidAt);
        return order;
    }

    private void processDue(LocalDateTime now) {
        assertThat(context.containsBean("commercialSubscriptionGrantService"))
            .as("periodic subscription grant service")
            .isTrue();
        ReflectionTestUtils.invokeMethod(context.getBean("commercialSubscriptionGrantService"), "processDue", now);
    }

    private Object resolveDiscount(Long tenantId, LocalDateTime at) {
        assertThat(context.containsBean("commercialEntitlementResolver"))
            .as("commercial entitlement resolver")
            .isTrue();
        return ReflectionTestUtils.invokeMethod(
            context.getBean("commercialEntitlementResolver"), "resolveGlobalDiscount", tenantId, at);
    }

    private record SubscriptionWindow(LocalDateTime startsAt, LocalDateTime endsAt) {}
}
