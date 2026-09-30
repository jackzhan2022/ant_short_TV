package com.antshorttv.storage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.common.provider.DefaultCredentialsProvider;
import com.tencentcloudapi.sts.v20180813.StsClient;
import com.tencentcloudapi.sts.v20180813.models.GetFederationTokenRequest;
import org.springframework.stereotype.Component;

@Component
public class TencentTemporaryCosCredentialIssuer extends TemporaryCosCredentialIssuer {
    private final DefaultCredentialsProvider credentialsProvider = new DefaultCredentialsProvider();
    private final String region;

    public TencentTemporaryCosCredentialIssuer(ObjectStorageProperties properties) {
        this.region = properties.getRegion();
    }

    @Override
    public TemporaryCosCredentials issue(String name, String policy, long durationSeconds) {
        try {
            Credential credential = credentialsProvider.getCredentials();
            HttpProfile http = new HttpProfile();
            http.setEndpoint("sts.tencentcloudapi.com");
            ClientProfile profile = new ClientProfile();
            profile.setHttpProfile(http);
            StsClient client = new StsClient(credential, region, profile);
            GetFederationTokenRequest request = new GetFederationTokenRequest();
            request.setName(name);
            request.setPolicy(policy);
            request.setDurationSeconds(durationSeconds);
            var response = client.GetFederationToken(request);
            var value = response.getCredentials();
            return new TemporaryCosCredentials(
                value.getTmpSecretId(),
                value.getTmpSecretKey(),
                value.getToken(),
                response.getExpiredTime(),
                response.getRequestId()
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "COS 临时上传凭证签发失败。");
        }
    }
}
