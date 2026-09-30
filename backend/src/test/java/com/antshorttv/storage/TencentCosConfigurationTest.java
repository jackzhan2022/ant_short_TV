package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.qcloud.cos.ClientConfig;
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
}
