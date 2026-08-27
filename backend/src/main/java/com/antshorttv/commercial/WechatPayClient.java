package com.antshorttv.commercial;

import java.math.BigDecimal;

public abstract class WechatPayClient {
    public abstract WechatNativeOrder createNativeOrder(WechatNativeOrderRequest request);
    public abstract WechatPaymentStatus queryOrder(String merchantOrderNo);
    public abstract void closeOrder(String merchantOrderNo);
}

record WechatNativeOrderRequest(
    String merchantOrderNo,
    String description,
    BigDecimal amount,
    String currency
) {}

record WechatNativeOrder(String prepayId, String codeUrl) {}

record WechatPaymentStatus(
    String status,
    String providerTradeNo,
    BigDecimal amount,
    String currency,
    java.time.LocalDateTime paidAt,
    String rawResponse
) {}
