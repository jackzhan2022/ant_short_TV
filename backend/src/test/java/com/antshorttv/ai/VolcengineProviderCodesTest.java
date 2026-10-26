package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VolcengineProviderCodesTest {
    @Test
    void recognizesBothArkSuppliersWithoutChangingOtherProviderComparisons() {
        assertThat(VolcengineProviderCodes.isArk("volcengine_ark_xinji")).isTrue();
        assertThat(VolcengineProviderCodes.isArk("VOLCENGINE_ARK")).isTrue();
        assertThat(VolcengineProviderCodes.isArk(null)).isFalse();
        assertThat(VolcengineProviderCodes.isArk("OpenAI")).isFalse();
        assertThat(VolcengineProviderCodes.adapterCode("VOLCENGINE_ARK_XINJI")).isEqualTo("VOLCENGINE_ARK");
        assertThat(VolcengineProviderCodes.adapterCode("OpenAI")).isEqualTo("OpenAI");
        assertThat(VolcengineProviderCodes.matches("VOLCENGINE_ARK", "VOLCENGINE_ARK_XINJI")).isTrue();
        assertThat(VolcengineProviderCodes.matches("RUNNING", "running")).isTrue();
        assertThat(VolcengineProviderCodes.matches("SUCCEEDED", "VOLCENGINE_ARK_XINJI")).isFalse();
    }
}
