package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.transfer.TransferManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class TencentCosConfigurationTest {

    @Test
    void usesTheSameRegionCosInternalEndpoint() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setRegion("ap-guangzhou");

        ClientConfig config = TencentCosConfiguration.clientConfig(properties);

        assertThat(config.getEndPointSuffix())
            .isEqualTo("cos-internal.ap-guangzhou.tencentcos.cn");
        assertThat(config.getEndpointBuilder().buildGeneralApiEndpoint("antv-1418200553"))
            .isEqualTo("antv-1418200553.cos-internal.ap-guangzhou.tencentcos.cn");
    }

    @Test
    void managedMultipartCompletionProtectsDurableOriginalWithFreshSdkRequest() {
        COS delegate = org.mockito.Mockito.mock(COS.class);
        org.mockito.Mockito.when(delegate.getClientConfig()).thenReturn(new ClientConfig(new com.qcloud.cos.region.Region("ap-guangzhou")));
        TransferManager manager = new TencentCosConfiguration().cosTransferManager(delegate);
        try {
            CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                "antv-1418200553", "materials/0/style/202610/44/v1/original.png", "multipart-1", List.of());

            manager.getCOSClient().completeMultipartUpload(request);

            assertThat(request.getCustomRequestHeaders()).containsEntry("x-cos-forbid-overwrite", "true");
            org.mockito.Mockito.verify(delegate).completeMultipartUpload(request);
        } finally {
            manager.shutdownNow();
        }
    }

    @Test
    void multipartPolicyLeavesStagingAndDerivedWritesUnchangedAndUnwrapsProviderErrors() {
        COS delegate = org.mockito.Mockito.mock(COS.class);
        ClientConfig config = new ClientConfig(new com.qcloud.cos.region.Region("ap-guangzhou"));
        org.mockito.Mockito.when(delegate.getClientConfig()).thenReturn(config);
        TransferManager manager = new TencentCosConfiguration().cosTransferManager(delegate);
        try {
            CompleteMultipartUploadRequest staging = new CompleteMultipartUploadRequest(
                "antv-1418200553", "uploads/11/session/source.png", "multipart-1", List.of());
            CompleteMultipartUploadRequest derived = new CompleteMultipartUploadRequest(
                "antv-1418200553", "materials/0/style/202610/44/v1/derived/display.png", "multipart-2", List.of());
            manager.getCOSClient().completeMultipartUpload(staging);
            manager.getCOSClient().completeMultipartUpload(derived);
            assertThat(staging.getCustomRequestHeaders()).isNullOrEmpty();
            assertThat(derived.getCustomRequestHeaders()).isNullOrEmpty();
            assertThat(manager.getCOSClient().getClientConfig()).isSameAs(config);
            RuntimeException failure = new com.qcloud.cos.exception.CosClientException("Provider rejected completion");
            org.mockito.Mockito.doThrow(failure).when(delegate).completeMultipartUpload(staging);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.getCOSClient().completeMultipartUpload(staging))
                .isSameAs(failure);
        } finally {
            manager.shutdownNow();
        }
    }

    @Test
    void managedSinglePutAlsoProtectsDurableOriginal() {
        COS delegate = org.mockito.Mockito.mock(COS.class);
        org.mockito.Mockito.when(delegate.getClientConfig()).thenReturn(new ClientConfig(new com.qcloud.cos.region.Region("ap-guangzhou")));
        TransferManager manager = new TencentCosConfiguration().cosTransferManager(delegate);
        try {
            com.qcloud.cos.model.PutObjectRequest request = new com.qcloud.cos.model.PutObjectRequest(
                "antv-1418200553", "materials/0/style/202610/44/v1/original.png",
                new java.io.ByteArrayInputStream(new byte[] {1}), new com.qcloud.cos.model.ObjectMetadata());

            manager.getCOSClient().putObject(request);

            assertThat(request.getCustomRequestHeaders()).containsEntry("x-cos-forbid-overwrite", "true");
            org.mockito.Mockito.verify(delegate).putObject(request);
        } finally {
            manager.shutdownNow();
        }
    }
}
