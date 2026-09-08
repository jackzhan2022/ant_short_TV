package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.antshorttv.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "commercial.wechat.enabled=true")
class CommercialPackageServiceTest {
    @Autowired CommercialPackageService service;
    @Autowired CommercialEntitlementCatalogService catalogService;
    @Autowired CommercialOrderService orderService;
    @Autowired CommercialEntitlementOrchestrator orchestrator;
    @Autowired CommercialEntitlementResolver entitlementResolver;
    @Autowired CommercialPaymentLifecycleService paymentLifecycleService;
    @Autowired WechatPaymentNotificationService notificationService;
    @Autowired JdbcTemplate jdbc;
    @MockBean WechatPayClient wechatPayClient;
    @MockBean WechatPayNotificationVerifier notificationVerifier;

    @BeforeEach
    void configureWechatNativeOrder() {
        when(wechatPayClient.createNativeOrder(any()))
            .thenReturn(new WechatNativeOrder("wx-prepay", "weixin://wxpay/test-code"));
    }

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
        assertThat(published.price()).isEqualByComparingTo("588.00");
        assertThat(published.currency()).isEqualTo("CNY");
        assertThat(published.effectiveFrom()).isCloseTo(draft.effectiveFrom(), within(1, ChronoUnit.MICROS));
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

        CommercialPackageSummaryResponse summary = service.listPackages().stream()
            .filter(item -> "POINT_PACK".equals(item.code()))
            .findFirst()
            .orElseThrow();
        assertThat(summary.latestVersionNo()).isEqualTo(2);
        assertThat(summary.latestName()).isEqualTo("积分包新版");
        assertThat(summary.latestPrice()).isEqualByComparingTo("120.00");
        assertThat(summary.latestStatus()).isEqualTo("DRAFT");

        assertThatThrownBy(() -> service.createDraft(new CommercialPackageDraftCommand(
            "BAD", "SUBSCRIPTION", "错误套餐", null, "MONTH", 1,
            BigDecimal.TEN, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("FREE_GENERATIONS", BigDecimal.ONE)), 2L
        ))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("权益不存在");

        assertThatThrownBy(() -> service.createDraft(new CommercialPackageDraftCommand(
            "BAD_PERIOD", "SUBSCRIPTION", "错误周期", null, "MONTH", 0,
            BigDecimal.TEN, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("PERIODIC_POINTS", BigDecimal.ONE)), 2L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("周期月数");
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
        assertThat(order.merchantOrderNo()).hasSizeLessThanOrEqualTo(32);
        assertThat(order.codeUrl()).isEqualTo("weixin://wxpay/test-code");
        assertThat(jdbc.queryForObject(
            "select code_url from commercial_payment where order_id=?", String.class, order.id()))
            .isEqualTo("weixin://wxpay/test-code");
    }

    @Test
    void listsPlatformOrdersWithTenantPackageAndPaymentDetails() {
        CommercialPackageVersionResponse version = publishedPointPackage("PLATFORM_ORDER_PACK", "88.00", "880");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(91L, 92L, version.versionId()));

        PlatformCommercialOrderPageResponse page = orderService.listPlatform(
            PlatformCommercialOrderQuery.of("PLATFORM_ORDER_PACK", "PENDING_PAYMENT", "POINT_PACKAGE", 1, 20));

        assertThat(page.total()).isEqualTo(1);
        PlatformCommercialOrderSummaryResponse item = page.records().get(0);
        assertThat(item.id()).isEqualTo(order.id());
        assertThat(item.packageName()).isEqualTo("PLATFORM_ORDER_PACK");
        assertThat(item.payment().provider()).isEqualTo("WECHAT_NATIVE");

        jdbc.update("update commercial_package_version set name=? where id=?", "后续改名", version.versionId());
        assertThat(orderService.listPlatform(
            PlatformCommercialOrderQuery.of("COM", "PENDING_PAYMENT", "POINT_PACKAGE", 1, 20))
            .records().get(0).packageName()).isEqualTo("PLATFORM_ORDER_PACK");
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
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where tenant_id=41 and order_id=?",
            Integer.class, first.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from point_ledger where tenant_id=41 and entry_type='GRANT'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void generatesPackageCodeWhenCodeIsMissing() {
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            null, "POINT_PACKAGE", "自动编码套餐", null, null, null,
            BigDecimal.ONE, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS", BigDecimal.ONE)), 9L
        ));
        assertThat(jdbc.queryForObject("select code from commercial_package where id=?", String.class, draft.packageId()))
            .matches("PKG-[A-Z0-9]{12}");
    }

    @Test
    void snapshotsValuelessDisplayEntitlementFromActiveCatalog() {
        CommercialEntitlementDefinitionResponse display = catalogService.create(
            new CommercialDisplayEntitlementCommand("使用全部模型", "仅用于套餐介绍", 40));

        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            "DISPLAY_PACK", "SUBSCRIPTION", "展示权益套餐", null, "MONTH", 1,
            BigDecimal.TEN, null, "CNY", LocalDateTime.now(), null,
            List.of(
                new CommercialEntitlementInput("PERIODIC_POINTS", new BigDecimal("100")),
                new CommercialEntitlementInput(display.code(), null)
            ), 10L
        ));

        CommercialEntitlementInput snapshot = draft.entitlements().stream()
            .filter(item -> display.code().equals(item.type())).findFirst().orElseThrow();
        assertThat(snapshot.value()).isNull();
        assertThat(snapshot.name()).isEqualTo("使用全部模型");
        assertThat(snapshot.category()).isEqualTo("DISPLAY");
        assertThat(jdbc.queryForObject("""
            select text_value from commercial_entitlement
             where package_version_id=? and entitlement_type=?
            """, String.class, draft.versionId(), display.code())).isEqualTo("使用全部模型");

        catalogService.update(display.id(),
            new CommercialDisplayEntitlementCommand("使用所有模型", null, 40));
        assertThat(service.history(draft.packageId()).get(0).entitlements())
            .extracting(CommercialEntitlementInput::name).contains("使用全部模型");
    }

    @Test
    void rejectsInvalidCatalogEntitlementsAndRevalidatesOnPublish() {
        CommercialEntitlementDefinitionResponse display = catalogService.create(
            new CommercialDisplayEntitlementCommand("专属展示权益", null, 50));

        assertThatThrownBy(() -> createEntitlementValidationDraft("UNKNOWN", null))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
        assertThatThrownBy(() -> createEntitlementValidationDraft(display.code(), BigDecimal.ONE))
            .isInstanceOf(BusinessException.class).hasMessageContaining("无需填写数值");
        assertThatThrownBy(() -> createEntitlementValidationDraft("ONE_TIME_POINTS", null))
            .isInstanceOf(BusinessException.class).hasMessageContaining("权益值");
        assertThatThrownBy(() -> service.createDraft(new CommercialPackageDraftCommand(
            "DUPLICATE_ENTITLEMENT", "POINT_PACKAGE", "重复权益", null, null, null,
            BigDecimal.ONE, null, "CNY", LocalDateTime.now(), null,
            List.of(
                new CommercialEntitlementInput("ONE_TIME_POINTS", BigDecimal.ONE),
                new CommercialEntitlementInput("ONE_TIME_POINTS", BigDecimal.TEN)
            ), 10L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("重复");

        CommercialPackageVersionResponse draft = createEntitlementValidationDraft(display.code(), null);
        catalogService.disable(display.id());
        assertThatThrownBy(() -> service.publish(draft.packageId(), draft.versionId(), 10L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("已停用");
        assertThatThrownBy(() -> createEntitlementValidationDraft(display.code(), null))
            .isInstanceOf(BusinessException.class).hasMessageContaining("已停用");
    }

    @Test
    void fallsBackToDictionaryNameForLegacySystemEntitlement() {
        CommercialPackageVersionResponse draft = createEntitlementValidationDraft(
            "ONE_TIME_POINTS", BigDecimal.TEN);
        jdbc.update("update commercial_entitlement set text_value=null where package_version_id=?", draft.versionId());

        CommercialEntitlementInput entitlement = service.history(draft.packageId()).get(0).entitlements().get(0);
        assertThat(entitlement.name()).isEqualTo("一次性积分");
        assertThat(entitlement.category()).isEqualTo("SYSTEM");
    }

    @Test
    void keepsDisplayEntitlementSnapshotInOrderAndSubscriptionWithoutExecutingIt() {
        CommercialEntitlementDefinitionResponse display = catalogService.create(
            new CommercialDisplayEntitlementCommand("使用全部模型（快照）", null, 60));
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            "SNAPSHOT_SUBSCRIPTION", "SUBSCRIPTION", "快照订阅", null, "MONTH", 1,
            new BigDecimal("30.00"), null, "CNY", LocalDateTime.now(), null,
            List.of(
                new CommercialEntitlementInput("PERIODIC_POINTS", new BigDecimal("100")),
                new CommercialEntitlementInput("GLOBAL_DISCOUNT", new BigDecimal("0.90")),
                new CommercialEntitlementInput(display.code(), null)
            ), 10L
        ));
        CommercialPackageVersionResponse published = service.publish(draft.packageId(), draft.versionId(), 10L);
        CommercialOrderResponse order = orderService.create(
            new CommercialOrderCommand(310L, 410L, published.versionId()));
        LocalDateTime paidAt = LocalDateTime.now();
        orchestrator.confirmPaid(order.id(), "WX-SNAPSHOT", new BigDecimal("30.00"), paidAt);

        catalogService.update(display.id(),
            new CommercialDisplayEntitlementCommand("改名后的展示权益", null, 60));
        catalogService.disable(display.id());

        String orderSnapshot = jdbc.queryForObject(
            "select package_snapshot_json from commercial_order where id=?", String.class, order.id());
        String subscriptionSnapshot = jdbc.queryForObject(
            "select snapshot_json from team_subscription where source_order_id=?", String.class, order.id());
        assertThat(orderSnapshot).contains("使用全部模型（快照）", display.code())
            .doesNotContain("改名后的展示权益");
        assertThat(subscriptionSnapshot).isEqualTo(orderSnapshot);
        assertThat(jdbc.queryForObject("""
            select count(*) from commercial_entitlement_grant
             where order_id=? and entitlement_type=?
            """, Integer.class, order.id(), display.code())).isZero();
        assertThat(entitlementResolver.resolveGlobalDiscount(310L, paidAt.plusSeconds(1)).discountRate())
            .isEqualByComparingTo("0.90000000");
    }

    @Test
    void completesDisplayOnlyPackageWithoutCreatingGrant() {
        CommercialEntitlementDefinitionResponse display = catalogService.create(
            new CommercialDisplayEntitlementCommand("展示权益不发放", null, 70));
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            "DISPLAY_ONLY", "POINT_PACKAGE", "纯展示套餐", null, null, null,
            new BigDecimal("1.00"), null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput(display.code(), null)), 10L
        ));
        service.publish(draft.packageId(), draft.versionId(), 10L);
        CommercialOrderResponse order = orderService.create(
            new CommercialOrderCommand(311L, 411L, draft.versionId()));

        CommercialOrderEntity completed = orchestrator.confirmPaid(
            order.id(), "WX-DISPLAY-ONLY", new BigDecimal("1.00"), LocalDateTime.now());

        assertThat(completed.status).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
            "select count(*) from commercial_entitlement_grant where order_id=?",
            Integer.class, order.id())).isZero();
    }

    @Test
    void proactiveLookupCompletesPaidOrder() {
        CommercialPackageVersionResponse version = publishedPointPackage("LOOKUP_PACK", "40.00", "400");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(51L, 52L, version.versionId()));
        when(wechatPayClient.queryOrder(order.merchantOrderNo())).thenReturn(new WechatPaymentStatus(
            "SUCCESS", "WX-LOOKUP", new BigDecimal("40.00"), "CNY", LocalDateTime.now(), "{}"));

        CommercialOrderEntity refreshed = paymentLifecycleService.refresh(order.id());

        assertThat(refreshed.status).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=51", BigDecimal.class))
            .isEqualByComparingTo("400");
    }

    @Test
    void closesExpiredUnpaidOrderAfterProviderLookup() {
        CommercialPackageVersionResponse version = publishedPointPackage("EXPIRED_PACK", "50.00", "500");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(61L, 62L, version.versionId()));
        when(wechatPayClient.queryOrder(order.merchantOrderNo())).thenReturn(new WechatPaymentStatus(
            "NOTPAY", null, new BigDecimal("50.00"), "CNY", null, "{}"));

        paymentLifecycleService.closeExpired(order.expiresAt().plusSeconds(1));

        assertThat(orderService.require(order.id()).status).isEqualTo("CLOSED");
        verify(wechatPayClient).closeOrder(order.merchantOrderNo());
        assertThat(orderService.active(61L)).isEmpty();
    }

    @Test
    void duplicateNotificationPersistsOneEventAndGrantsOnce() {
        CommercialPackageVersionResponse version = publishedPointPackage("CALLBACK_PACK", "60.00", "600");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(71L, 72L, version.versionId()));
        WechatPaymentNotification notification = new WechatPaymentNotification(
            "EVENT-DUP", order.merchantOrderNo(), "WX-DUP", new BigDecimal("60.00"), "CNY",
            LocalDateTime.now(), "{\"id\":\"EVENT-DUP\"}");
        when(notificationVerifier.verify(any(), any(), any(), any(), any())).thenReturn(notification);

        notificationService.process("1", "nonce", "signature", "serial", notification.rawBody());
        notificationService.process("1", "nonce", "signature", "serial", notification.rawBody());

        assertThat(jdbc.queryForObject("select count(*) from commercial_payment_event where provider_event_id='EVENT-DUP'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from commercial_entitlement_grant where tenant_id=71", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select processed from commercial_payment_event where provider_event_id='EVENT-DUP'", Boolean.class)).isTrue();
    }

    @Test
    void amountMismatchPersistsExceptionEvidence() {
        CommercialPackageVersionResponse version = publishedPointPackage("MISMATCH_PACK", "70.00", "700");
        CommercialOrderResponse order = orderService.create(new CommercialOrderCommand(81L, 82L, version.versionId()));
        WechatPaymentNotification notification = new WechatPaymentNotification(
            "EVENT-BAD", order.merchantOrderNo(), "WX-BAD-AMOUNT", new BigDecimal("69.99"), "CNY",
            LocalDateTime.now(), "{\"id\":\"EVENT-BAD\"}");
        when(notificationVerifier.verify(any(), any(), any(), any(), any())).thenReturn(notification);

        assertThatThrownBy(() -> notificationService.process("1", "nonce", "signature", "serial", notification.rawBody()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");

        assertThat(orderService.require(order.id()).status).isEqualTo("PAYMENT_EXCEPTION");
        assertThat(jdbc.queryForObject("select processed from commercial_payment_event where provider_event_id='EVENT-BAD'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from commercial_audit where target_id=? and operation='PAYMENT_EXCEPTION'", Integer.class, order.id())).isEqualTo(1);
    }


    private CommercialPackageVersionResponse publishedPointPackage(String code, String price, String points) {
        CommercialPackageVersionResponse draft = service.createDraft(new CommercialPackageDraftCommand(
            code, "POINT_PACKAGE", code, null, null, null,
            new BigDecimal(price), null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS", new BigDecimal(points))), 3L
        ));
        return service.publish(draft.packageId(), draft.versionId(), 3L);
    }

    private CommercialPackageVersionResponse createEntitlementValidationDraft(String type, BigDecimal value) {
        return service.createDraft(new CommercialPackageDraftCommand(
            null, "POINT_PACKAGE", "权益校验套餐", null, null, null,
            BigDecimal.ONE, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput(type, value)), 10L
        ));
    }
}
