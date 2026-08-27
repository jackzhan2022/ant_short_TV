package com.antshorttv.commercial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class WechatPayNotificationVerifier {
    private final WechatPayProperties properties;
    private final ObjectMapper objectMapper;
    private final WechatPaySdkProvider sdkProvider;

    public WechatPayNotificationVerifier(
        WechatPayProperties properties,
        ObjectMapper objectMapper,
        WechatPaySdkProvider sdkProvider
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sdkProvider = sdkProvider;
    }

    public WechatPaymentNotification verify(
        String timestamp,
        String nonce,
        String signature,
        String serial,
        String body
    ) {
        try {
            if (blank(timestamp) || blank(nonce) || blank(signature) || blank(serial)) {
                throw new IllegalArgumentException("Missing WeChat signature headers");
            }
            RequestParam request = new RequestParam.Builder()
                .timestamp(timestamp)
                .nonce(nonce)
                .signature(signature)
                .serialNumber(serial)
                .body(body)
                .build();
            Transaction transaction = sdkProvider.get().parseNotification(request);
            if (transaction == null || transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
                throw new IllegalArgumentException("WeChat notification is not a successful payment");
            }
            if (!properties.getMerchantId().equals(transaction.getMchid())) {
                throw new IllegalArgumentException("WeChat merchant ID mismatch");
            }
            if (!properties.getAppId().equals(transaction.getAppid())) {
                throw new IllegalArgumentException("WeChat app ID mismatch");
            }
            TransactionAmount amount = transaction.getAmount();
            if (amount == null || amount.getTotal() == null) {
                throw new IllegalArgumentException("WeChat notification amount is missing");
            }
            JsonNode envelope = objectMapper.readTree(body);
            return new WechatPaymentNotification(
                envelope.path("id").asText(),
                transaction.getOutTradeNo(),
                transaction.getTransactionId(),
                BigDecimal.valueOf(amount.getTotal(), 2),
                amount.getCurrency(),
                OffsetDateTime.parse(transaction.getSuccessTime()).toLocalDateTime(),
                body);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid WeChat payment notification", exception);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}

record WechatPaymentNotification(
    String eventId,
    String merchantOrderNo,
    String providerTradeNo,
    BigDecimal amount,
    String currency,
    java.time.LocalDateTime paidAt,
    String rawBody
) {}
