package com.antshorttv.commercial;

import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
class WechatPayV3Client extends WechatPayClient {
    private final WechatPayProperties properties;
    private final WechatPaySdkProvider sdkProvider;

    WechatPayV3Client(WechatPayProperties properties, WechatPaySdkProvider sdkProvider) {
        this.properties = properties;
        this.sdkProvider = sdkProvider;
    }

    @Override
    public WechatNativeOrder createNativeOrder(WechatNativeOrderRequest request) {
        requireOrderConfiguration();
        try {
            Amount amount = new Amount();
            amount.setTotal(request.amount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact());
            amount.setCurrency(request.currency());

            PrepayRequest sdkRequest = new PrepayRequest();
            sdkRequest.setAppid(properties.getAppId());
            sdkRequest.setMchid(properties.getMerchantId());
            sdkRequest.setDescription(request.description());
            sdkRequest.setOutTradeNo(request.merchantOrderNo());
            sdkRequest.setNotifyUrl(properties.getNotifyUrl());
            sdkRequest.setAmount(amount);

            PrepayResponse response = sdkProvider.get().prepay(sdkRequest);
            if (response == null || blank(response.getCodeUrl())) {
                throw new IllegalStateException("WeChat Native response missing code_url");
            }
            return new WechatNativeOrder(null, response.getCodeUrl());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("WeChat Native order failed", exception);
        }
    }

    @Override
    public WechatPaymentStatus queryOrder(String merchantOrderNo) {
        requireMerchantConfiguration();
        try {
            QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
            request.setOutTradeNo(merchantOrderNo);
            request.setMchid(properties.getMerchantId());
            Transaction transaction = sdkProvider.get().queryOrderByOutTradeNo(request);
            TransactionAmount amount = transaction.getAmount();
            LocalDateTime paidAt = blank(transaction.getSuccessTime())
                ? null : OffsetDateTime.parse(transaction.getSuccessTime()).toLocalDateTime();
            return new WechatPaymentStatus(
                transaction.getTradeState() == null ? null : transaction.getTradeState().name(),
                transaction.getTransactionId(),
                amount == null || amount.getTotal() == null ? null : BigDecimal.valueOf(amount.getTotal(), 2),
                amount == null ? null : amount.getCurrency(),
                paidAt,
                transaction.toString());
        } catch (Exception exception) {
            throw new IllegalStateException("WeChat query order failed", exception);
        }
    }

    @Override
    public void closeOrder(String merchantOrderNo) {
        requireMerchantConfiguration();
        try {
            CloseOrderRequest request = new CloseOrderRequest();
            request.setOutTradeNo(merchantOrderNo);
            request.setMchid(properties.getMerchantId());
            sdkProvider.get().closeOrder(request);
        } catch (Exception exception) {
            throw new IllegalStateException("WeChat close order failed", exception);
        }
    }

    private void requireOrderConfiguration() {
        requireMerchantConfiguration();
        if (blank(properties.getAppId()) || blank(properties.getNotifyUrl())) {
            throw new IllegalStateException("WeChat Pay order configuration is incomplete");
        }
    }

    private void requireMerchantConfiguration() {
        if (blank(properties.getMerchantId())) {
            throw new IllegalStateException("WeChat Pay merchant configuration is incomplete");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
