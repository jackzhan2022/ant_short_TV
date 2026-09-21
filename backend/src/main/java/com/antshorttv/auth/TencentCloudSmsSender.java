package com.antshorttv.auth;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import org.springframework.stereotype.Service;

@Service
public class TencentCloudSmsSender extends SmsSender {

    private final TencentSmsProperties properties;

    public TencentCloudSmsSender(TencentSmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendRegistrationVerificationCode(String mobile, String verificationCode) {
        try {
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("sms.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            SmsClient client = new SmsClient(
                new Credential(properties.secretId(), properties.secretKey()),
                properties.region(),
                clientProfile
            );
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumberSet(new String[] {"+86" + mobile});
            request.setSmsSdkAppId(properties.sdkAppId());
            request.setSignName(properties.signName());
            request.setTemplateId(properties.registerTemplateId());
            request.setTemplateParamSet(new String[] {verificationCode});
            SendSmsResponse response = client.SendSms(request);
            if (response.getSendStatusSet().length != 1 || !"Ok".equals(response.getSendStatusSet()[0].getCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码发送失败，请稍后重试。");
            }
        } catch (TencentCloudSDKException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码发送失败，请稍后重试。");
        }
    }
}
