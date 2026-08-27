package com.antshorttv.commercial;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "commercial.wechat")
public class WechatPayProperties {
    private boolean enabled;
    private String baseUrl = "https://api.mch.weixin.qq.com";
    private String appId;
    private String merchantId;
    private String merchantSerialNumber;
    private String merchantPrivateKeyPath;
    private String apiV3Key;
    private String notifyUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getMerchantSerialNumber() { return merchantSerialNumber; }
    public void setMerchantSerialNumber(String merchantSerialNumber) { this.merchantSerialNumber = merchantSerialNumber; }
    public String getMerchantPrivateKeyPath() { return merchantPrivateKeyPath; }
    public void setMerchantPrivateKeyPath(String merchantPrivateKeyPath) { this.merchantPrivateKeyPath = merchantPrivateKeyPath; }
    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
}
