package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssetIdentityNormalizerTest {
    @Test
    void normalizesChineseNamesByRemovingWhitespaceAndPunctuation() {
        assertThat(AssetIdentityNormalizer.normalize("  林 小满（雨夜）！ "))
            .isEqualTo("林小满雨夜");
    }

    @Test
    void normalizesLatinCaseWithoutChangingChineseCharacters() {
        assertThat(AssetIdentityNormalizer.normalize("VIP-室 A"))
            .isEqualTo("vip室a");
    }
}
