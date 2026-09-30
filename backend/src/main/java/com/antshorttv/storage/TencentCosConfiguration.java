package com.antshorttv.storage;

import com.qcloud.cos.COS;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.provider.DefaultCredentialsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TencentCosConfiguration {

    @Bean(destroyMethod = "shutdown")
    public COS cosClient(
        ObjectStorageProperties properties,
        CosStorageMetrics metrics,
        COSCredentialsProvider credentials
    ) {
        properties.validate();
        ClientConfig config = clientConfig(properties);
        metrics.internalEndpointConfigured(config.getEndPointSuffix().startsWith("cos-internal."));
        return new COSClient(credentials, config);
    }

    @Bean
    public COSCredentialsProvider cosCredentialsProvider() {
        return new TencentCloudCredentialsAdapter();
    }

    static ClientConfig clientConfig(ObjectStorageProperties properties) {
        ClientConfig config = new ClientConfig(new Region(properties.getRegion()));
        config.setHttpProtocol(HttpProtocol.https);
        config.setMaxErrorRetry(3);
        config.setCheckRequestPath(true);
        config.setEndPointSuffix(
            "cos-internal.%s.tencentcos.cn".formatted(properties.getRegion())
        );
        return config;
    }

    @Bean(destroyMethod = "shutdownNow")
    public TransferManager cosTransferManager(COS cos) {
        TransferManager manager = new TransferManager(cos);
        TransferManagerConfiguration configuration = new TransferManagerConfiguration();
        configuration.setMultipartUploadThreshold(16L * 1024 * 1024);
        configuration.setMinimumUploadPartSize(16L * 1024 * 1024);
        manager.setConfiguration(configuration);
        return manager;
    }

    static final class TencentCloudCredentialsAdapter implements COSCredentialsProvider {
        private final DefaultCredentialsProvider delegate = new DefaultCredentialsProvider();

        @Override
        public COSCredentials getCredentials() {
            try {
                Credential credential = delegate.getCredentials();
                if (credential.getToken() == null || credential.getToken().isBlank()) {
                    return new BasicCOSCredentials(credential.getSecretId(), credential.getSecretKey());
                }
                return new BasicSessionCredentials(
                    credential.getSecretId(), credential.getSecretKey(), credential.getToken()
                );
            } catch (Exception exception) {
                throw new CosClientException("无法获取腾讯云实例角色凭证。", exception);
            }
        }

        @Override
        public void refresh() {
            getCredentials();
        }
    }
}
