package com.antshorttv.commercial;

import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;

final class OfficialWechatPaySdk extends WechatPaySdk {
    private final NativePayService nativePayService;
    private final NotificationParser notificationParser;

    OfficialWechatPaySdk(NativePayService nativePayService, NotificationParser notificationParser) {
        this.nativePayService = nativePayService;
        this.notificationParser = notificationParser;
    }

    @Override
    public PrepayResponse prepay(PrepayRequest request) { return nativePayService.prepay(request); }

    @Override
    public Transaction queryOrderByOutTradeNo(QueryOrderByOutTradeNoRequest request) {
        return nativePayService.queryOrderByOutTradeNo(request);
    }

    @Override
    public void closeOrder(CloseOrderRequest request) { nativePayService.closeOrder(request); }

    @Override
    public Transaction parseNotification(RequestParam request) {
        return notificationParser.parse(request, Transaction.class);
    }
}
