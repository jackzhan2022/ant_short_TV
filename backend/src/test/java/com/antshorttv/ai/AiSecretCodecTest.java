package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiSecretCodecTest {

    @Test
    void rejectsBlankSecretKey() {
        assertThatThrownBy(() -> new AiSecretCodec(" "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("ai.secret-key must be configured.");
    }
}
