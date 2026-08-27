package com.antshorttv.commercial;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import org.springframework.stereotype.Component;

@Component
class OfficialWechatPaySdkFactory extends WechatPaySdkFactory {
    @Override
    public WechatPaySdk create(WechatPayProperties properties) {
        RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
            .merchantId(properties.getMerchantId())
            .privateKeyFromPath(properties.getMerchantPrivateKeyPath())
            .merchantSerialNumber(properties.getMerchantSerialNumber())
            .apiV3Key(properties.getApiV3Key())
            .build();
        NativePayService nativePayService = new NativePayService.Builder().config(config).build();
        return new OfficialWechatPaySdk(nativePayService, new NotificationParser(config));
    }
}
