package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WechatPayNotificationVerifierTest {
    private static final String BODY = "{\"id\":\"EVT-1001\",\"event_type\":\"TRANSACTION.SUCCESS\"}";
    private WechatPayProperties properties;
    private WechatPaySdk sdk;
    private WechatPayNotificationVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new WechatPayProperties();
        properties.setMerchantId("merchant-1001");
        properties.setAppId("app-1001");
        sdk = mock(WechatPaySdk.class);
        WechatPaySdkProvider provider = mock(WechatPaySdkProvider.class);
        when(provider.get()).thenReturn(sdk);
        verifier = new WechatPayNotificationVerifier(properties, new ObjectMapper(), provider);
    }

    @Test
    void mapsSdkVerifiedSuccessfulTransaction() {
        when(sdk.parseNotification(any())).thenReturn(successfulTransaction());

        WechatPaymentNotification result = verifier.verify(
            "1787745600", "notification-nonce", "signature", "platform-serial", BODY);

        assertThat(result.eventId()).isEqualTo("EVT-1001");
        assertThat(result.merchantOrderNo()).isEqualTo("COM-1001");
        assertThat(result.providerTradeNo()).isEqualTo("WX-TRADE-1001");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(result.currency()).isEqualTo("CNY");
        assertThat(result.paidAt()).isEqualTo(LocalDateTime.of(2026, 8, 26, 20, 0));
        assertThat(result.rawBody()).isEqualTo(BODY);

        ArgumentCaptor<RequestParam> captor = ArgumentCaptor.forClass(RequestParam.class);
        verify(sdk).parseNotification(captor.capture());
        assertThat(captor.getValue().getSerialNumber()).isEqualTo("platform-serial");
        assertThat(captor.getValue().getSignature()).isEqualTo("signature");
        assertThat(captor.getValue().getBody()).isEqualTo(BODY);
    }

    @Test
    void rejectsNonSuccessfulTradeState() {
        Transaction transaction = successfulTransaction();
        transaction.setTradeState(Transaction.TradeStateEnum.NOTPAY);
        when(sdk.parseNotification(any())).thenReturn(transaction);

        assertThatThrownBy(this::verifyNotification)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a successful payment");
    }

    @Test
    void rejectsPaymentBelongingToAnotherMerchant() {
        Transaction transaction = successfulTransaction();
        transaction.setMchid("other-merchant");
        when(sdk.parseNotification(any())).thenReturn(transaction);

        assertThatThrownBy(this::verifyNotification)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("merchant ID mismatch");
    }

    @Test
    void rejectsPaymentBelongingToAnotherApp() {
        Transaction transaction = successfulTransaction();
        transaction.setAppid("other-app");
        when(sdk.parseNotification(any())).thenReturn(transaction);

        assertThatThrownBy(this::verifyNotification)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("app ID mismatch");
    }

    @Test
    void rejectsSignatureCertificateOrCipherFailureFromSdk() {
        when(sdk.parseNotification(any())).thenThrow(new ValidationException("invalid notification evidence"));

        assertThatThrownBy(this::verifyNotification)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid WeChat payment notification");
    }

    private WechatPaymentNotification verifyNotification() {
        return verifier.verify("1787745600", "nonce", "signature", "serial", BODY);
    }

    private Transaction successfulTransaction() {
        Transaction transaction = new Transaction();
        transaction.setTradeState(Transaction.TradeStateEnum.SUCCESS);
        transaction.setMchid("merchant-1001");
        transaction.setAppid("app-1001");
        transaction.setOutTradeNo("COM-1001");
        transaction.setTransactionId("WX-TRADE-1001");
        transaction.setSuccessTime("2026-08-26T20:00:00+08:00");
        TransactionAmount amount = new TransactionAmount();
        amount.setTotal(3000);
        amount.setCurrency("CNY");
        transaction.setAmount(amount);
        return transaction;
    }
}
