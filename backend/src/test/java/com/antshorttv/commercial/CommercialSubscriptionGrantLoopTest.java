package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.antshorttv.points.PointAccountingService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CommercialSubscriptionGrantLoopTest {
    private final TeamSubscriptionMapper subscriptions = mock(TeamSubscriptionMapper.class);
    private final CommercialEntitlementMapper entitlements = mock(CommercialEntitlementMapper.class);
    private final CommercialEntitlementGrantMapper grants = mock(CommercialEntitlementGrantMapper.class);
    private final CommercialOrderMapper orders = mock(CommercialOrderMapper.class);
    private final PointAccountingService accounting = mock(PointAccountingService.class);
    private final AdminTenantGrantMapper adminGrants = mock(AdminTenantGrantMapper.class);
    private final CommercialSubscriptionGrantService service = new CommercialSubscriptionGrantService(
        subscriptions, entitlements, grants, orders, accounting, adminGrants);

    @Test
    void missingPeriodicEntitlementClearsDueDateAndContinuesOtherSubscriptions() {
        TeamSubscriptionEntity broken = subscription(1L);
        TeamSubscriptionEntity expired = subscription(2L);
        expired.nextGrantAt = null;
        expired.endsAt = broken.nextGrantAt;
        when(subscriptions.selectList(any(Wrapper.class))).thenReturn(List.of(broken, expired), List.of());
        AtomicInteger lookups = new AtomicInteger();
        when(entitlements.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            // Bound the old infinite loop so a regression fails without hanging the test JVM.
            if (lookups.incrementAndGet() > 2) throw new AssertionError("Repeated the same missing entitlement");
            return null;
        });

        service.processDue(broken.startsAt.plusMonths(2));

        assertThat(broken.nextGrantAt).isNull();
        assertThat(lookups.get()).isEqualTo(1);
        assertThat(expired.status).isEqualTo("EXPIRED");
        verify(subscriptions).updateById(expired);
        verify(grants, never()).insert(any(CommercialEntitlementGrantEntity.class));
        verifyNoInteractions(accounting, orders, adminGrants);
    }

    @Test
    void unchangedGrantDateStopsCatchUpAndContinuesOtherSubscriptions() {
        TeamSubscriptionEntity broken = subscription(1L);
        broken.sourceOrderId = 10L;
        broken.nextGrantAt = broken.startsAt.plusMonths(2);
        LocalDateTime dueAt = broken.nextGrantAt;
        TeamSubscriptionEntity expired = subscription(2L);
        expired.nextGrantAt = null;
        expired.endsAt = dueAt;
        when(subscriptions.selectList(any(Wrapper.class))).thenReturn(List.of(broken, expired), List.of());
        CommercialEntitlementGrantEntity latest = new CommercialEntitlementGrantEntity();
        latest.periodNo = 1;
        latest.status = "GRANTED";
        // Simulate a grant cursor that does not advance after saving a grant.
        when(grants.selectOne(any(Wrapper.class))).thenReturn(latest, null, latest, null);
        CommercialEntitlementEntity entitlement = new CommercialEntitlementEntity();
        entitlement.numericValue = BigDecimal.ZERO;
        AtomicInteger lookups = new AtomicInteger();
        when(entitlements.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            if (lookups.incrementAndGet() > 1) throw new AssertionError("Repeated an unchanged grant date");
            return entitlement;
        });
        CommercialOrderEntity order = new CommercialOrderEntity();
        order.id = broken.sourceOrderId;
        order.userId = 20L;
        when(orders.selectById(broken.sourceOrderId)).thenReturn(order);

        service.processDue(dueAt);

        assertThat(broken.nextGrantAt).isEqualTo(dueAt);
        assertThat(expired.status).isEqualTo("EXPIRED");
        verify(subscriptions).updateById(expired);
        verify(grants).insert(any(CommercialEntitlementGrantEntity.class));
        verifyNoInteractions(accounting);
    }

    private TeamSubscriptionEntity subscription(Long id) {
        TeamSubscriptionEntity subscription = new TeamSubscriptionEntity();
        subscription.id = id;
        subscription.tenantId = id;
        subscription.packageVersionId = id;
        subscription.status = "ACTIVE";
        subscription.startsAt = LocalDateTime.of(2026, 8, 27, 23, 42, 17);
        subscription.endsAt = subscription.startsAt.plusYears(1);
        subscription.nextGrantAt = subscription.startsAt.plusMonths(1);
        return subscription;
    }
}
