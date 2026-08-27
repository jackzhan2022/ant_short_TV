package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WechatPayV3ClientTest {
    private WechatPayProperties properties;
    private WechatPaySdk sdk;
    private WechatPayV3Client client;

    @BeforeEach
    void setUp() {
        properties = new WechatPayProperties();
        properties.setAppId("app-1001");
        properties.setMerchantId("merchant-1001");
        properties.setNotifyUrl("https://pay.example.com/api/commercial/payments/wechat/notify");
        sdk = mock(WechatPaySdk.class);
        WechatPaySdkProvider provider = mock(WechatPaySdkProvider.class);
        when(provider.get()).thenReturn(sdk);
        client = new WechatPayV3Client(properties, provider);
    }

    @Test
    void createsNativeOrderThroughSdk() {
        PrepayResponse sdkResponse = new PrepayResponse();
        sdkResponse.setCodeUrl("weixin://wxpay/code-1001");
        when(sdk.prepay(org.mockito.ArgumentMatchers.any())).thenReturn(sdkResponse);

        WechatNativeOrder result = client.createNativeOrder(new WechatNativeOrderRequest(
            "COM-1001", "测试积分包", new BigDecimal("30.00"), "CNY"));

        assertThat(result.codeUrl()).isEqualTo("weixin://wxpay/code-1001");
        ArgumentCaptor<PrepayRequest> captor = ArgumentCaptor.forClass(PrepayRequest.class);
        verify(sdk).prepay(captor.capture());
        assertThat(captor.getValue().getAppid()).isEqualTo("app-1001");
        assertThat(captor.getValue().getMchid()).isEqualTo("merchant-1001");
        assertThat(captor.getValue().getOutTradeNo()).isEqualTo("COM-1001");
        assertThat(captor.getValue().getNotifyUrl()).endsWith("/api/commercial/payments/wechat/notify");
        assertThat(captor.getValue().getAmount().getTotal()).isEqualTo(3000);
        assertThat(captor.getValue().getAmount().getCurrency()).isEqualTo("CNY");
    }

    @Test
    void mapsSdkOrderQueryResponse() {
        Transaction transaction = new Transaction();
        transaction.setTradeState(Transaction.TradeStateEnum.SUCCESS);
        transaction.setTransactionId("WX-1001");
        transaction.setSuccessTime("2026-08-26T20:00:00+08:00");
        TransactionAmount amount = new TransactionAmount();
        amount.setTotal(3000);
        amount.setCurrency("CNY");
        transaction.setAmount(amount);
        when(sdk.queryOrderByOutTradeNo(org.mockito.ArgumentMatchers.any())).thenReturn(transaction);

        WechatPaymentStatus result = client.queryOrder("COM-1001");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.providerTradeNo()).isEqualTo("WX-1001");
        assertThat(result.amount()).isEqualByComparingTo("30.00");
        assertThat(result.currency()).isEqualTo("CNY");
        assertThat(result.paidAt()).isEqualTo(LocalDateTime.of(2026, 8, 26, 20, 0));
        ArgumentCaptor<QueryOrderByOutTradeNoRequest> captor =
            ArgumentCaptor.forClass(QueryOrderByOutTradeNoRequest.class);
        verify(sdk).queryOrderByOutTradeNo(captor.capture());
        assertThat(captor.getValue().getOutTradeNo()).isEqualTo("COM-1001");
        assertThat(captor.getValue().getMchid()).isEqualTo("merchant-1001");
    }

    @Test
    void closesOrderThroughSdk() {
        client.closeOrder("COM-1001");

        ArgumentCaptor<CloseOrderRequest> captor = ArgumentCaptor.forClass(CloseOrderRequest.class);
        verify(sdk).closeOrder(captor.capture());
        assertThat(captor.getValue().getOutTradeNo()).isEqualTo("COM-1001");
        assertThat(captor.getValue().getMchid()).isEqualTo("merchant-1001");
    }

    @Test
    void wrapsSdkFailureWithoutCredentialValues() {
        when(sdk.prepay(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("provider failure"));

        assertThatThrownBy(() -> client.createNativeOrder(new WechatNativeOrderRequest(
            "COM-FAIL", "测试", BigDecimal.ONE, "CNY")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Native order failed")
            .hasMessageNotContaining("merchant-1001");
    }
}
