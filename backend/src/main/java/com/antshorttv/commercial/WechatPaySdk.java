package com.antshorttv.commercial;

import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;

abstract class WechatPaySdk {
    abstract PrepayResponse prepay(PrepayRequest request);
    abstract Transaction queryOrderByOutTradeNo(QueryOrderByOutTradeNoRequest request);
    abstract void closeOrder(CloseOrderRequest request);
    abstract Transaction parseNotification(RequestParam request);
}
