package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CommercialPackageServiceTest {
    @Autowired CommercialPackageService service;
    @Autowired CommercialOrderService orderService;
    @Autowired CommercialEntitlementOrchestrator orchestrator;
    @Autowired JdbcTemplate jdbc;

    @Test
    void publishesImmutableVersionWithSupportedEntitlements() {
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            "TEAM_QUARTER", "SUBSCRIPTION", "团队季卡", "季度会员", "QUARTER", 3,
            new BigDecimal("588.00"), new BigDecimal("779.00"), "CNY",
            LocalDateTime.now().plusMinutes(5), null,
            List.of(
                new CommercialEntitlementInput("PERIODIC_POINTS", new BigDecimal("2888")),
                new CommercialEntitlementInput("GLOBAL_DISCOUNT", new BigDecimal("0.90"))
            ), 1L
        ));

        CommercialPackageVersionResponse published = service.publish(draft.packageId(), draft.versionId(), 1L);

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.versionNo()).isEqualTo(1);
        assertThat(published.entitlements()).hasSize(2);
        assertThat(service.unpublish(draft.packageId(), draft.versionId()).status()).isEqualTo("OFF_SALE");
        assertThat(service.history(draft.packageId())).hasSize(1);
        assertThatThrownBy(() -> service.updateDraftName(published.versionId(), "不可修改"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsNextVersionAndRejectsUnsupportedEntitlement() {
        CommercialPackageVersionResponse first = service.createDraft(new CommercialPackageDraftCommand(
            "POINT_PACK", "POINT_PACKAGE", "积分包", null, null, null,
            new BigDecimal("100.00"), null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS", new BigDecimal("1000"))), 2L
        ));
        CommercialPackageVersionResponse second = service.createDraft(new CommercialPackageDraftCommand(
            "POINT_PACK", "POINT_PACKAGE", "积分包新版", null, null, null,
            new BigDecimal("120.00"), null, "CNY", LocalDateTime.now().plusDays(1), null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS", new BigDecimal("1200"))), 2L
        ));
        assertThat(second.versionNo()).isEqualTo(first.versionNo() + 1);

        assertThatThrownBy(() -> service.createDraft(new CommercialPackageDraftCommand(
            "BAD", "SUBSCRIPTION", "错误套餐", null, "MONTH", 1,
            BigDecimal.TEN, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("FREE_GENERATIONS", BigDecimal.ONE)), 2L
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported entitlement");

        assertThatThrownBy(() -> service.createDraft(new CommercialPackageDraftCommand(
            "BAD_PERIOD", "SUBSCRIPTION", "错误周期", null, "MONTH", 0,
            BigDecimal.TEN, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("PERIODIC_POINTS", BigDecimal.ONE)), 2L
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("period");
    }

    @Test
    void createsPendingOrderWithPackageSnapshot() {
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            "ORDER_PACK", "POINT_PACKAGE", "订单积分包", null, null, null,
            new BigDecimal("20.00"), null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS", new BigDecimal("200"))), 3L
        ));
        service.publish(draft.packageId(), draft.versionId(), 3L);
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(11L, 22L, draft.versionId()));
        assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.merchantOrderNo()).startsWith("COM");
    }

    @Test
    void confirmsPointPackageExactlyOnceAndRejectsAmountMismatch() {
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            "GRANT_PACK", "POINT_PACKAGE", "发放积分包", null, null, null,
            new BigDecimal("30.00"), null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS", new BigDecimal("300"))), 4L
        ));
        service.publish(draft.packageId(), draft.versionId(), 4L);
        CommercialOrderResponse first = orderService.create(new CommercialOrderCommand(41L, 42L, draft.versionId()));
        assertThatThrownBy(() -> orchestrator.confirmPaid(first.id(), "WX-BAD", new BigDecimal("29.99"), LocalDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");

        orchestrator.confirmPaid(first.id(), "WX-OK", new BigDecimal("30.00"), LocalDateTime.now());
        orchestrator.confirmPaid(first.id(), "WX-OK", new BigDecimal("30.00"), LocalDateTime.now());
        BigDecimal balance = jdbc.queryForObject("select balance from team_point_account where tenant_id=41", BigDecimal.class);
        assertThat(balance).isEqualByComparingTo("300");
    }
}
