package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ObjectStoragePropertiesTest {

    @Test
    void acceptsProductionCosAndCdnConfiguration() {
        ObjectStorageProperties properties = validProperties();

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingBucketRegionOrSigningKey() {
        ObjectStorageProperties properties = validProperties();
        properties.setBucket(" ");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties = validProperties();
        properties.setRegion(null);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties = validProperties();
        properties.setCdnTypeDKey("");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    private ObjectStorageProperties validProperties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        properties.setCdnDomain("https://antvcdn.aixmax.cn");
        properties.setCdnTypeDKey("test-key");
        return properties;
    }
}
