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
    @Autowired CommercialOrderService orderService;
    @Autowired CommercialEntitlementOrchestrator orchestrator;
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

        assertThatThrownBy(() -> service.createDraft(new CommercialPackageDraftCommand(
            "BAD", "SUBSCRIPTION", "错误套餐", null, "MONTH", 1,
            BigDecimal.TEN, null, "CNY", LocalDateTime.now(), null,
            List.of(new CommercialEntitlementInput("FREE_GENERATIONS", BigDecimal.ONE)), 2L
        ))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持的权益类型");

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
}
